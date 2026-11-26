# Connect your server

Pay Relay sends each wallet payment SMS to one HTTPS address on your server. This page explains the request, what to answer, and gives copy-ready code.

- [The request](#the-request)
- [What to answer](#what-to-answer)
- [Reading the message](#reading-the-message)
- [Matching it to an order](#matching-it-to-an-order)
- Examples: [Node.js](#nodejs-express) · [PHP](#php) · [Laravel](#laravel) · [Python](#python-flask)
- [Testing without a phone](#testing-without-a-phone)
- [Checklist](#checklist)

## The request

```http
POST /api/payments/sms HTTP/1.1
Host: your-site.com
Authorization: Bearer 6f1c…your key…9a2e
Content-Type: application/json
User-Agent: PayRelay/1.1.0 (Android)

{
  "from": "bKash",
  "text": "You have received Tk 1,490.00 from 01711111111. Fee Tk 0.00. Balance Tk 5,000.00. TrxID CKA1B2C3D4 at 24/11/2026 16:45",
  "receivedAt": 1795517100000
}
```

| Field | Meaning |
| --- | --- |
| `from` | The SMS sender as the phone shows it: `bKash`, `NAGAD`, `16216` (Rocket). Also `PayRelay` for the heartbeat. |
| `text` | The full message, with line breaks and double spaces turned into single spaces. |
| `receivedAt` | When the phone received it, in milliseconds since 1970 (UTC). |

The key is the one you typed into the app. Compare it in constant time and reject anything else with `401`.

**Heartbeat.** When the app is set up, and every 15 minutes after that, it sends `{ "from": "PayRelay", "text": "ping" }`. Answer it like any message that is not a payment (`200` with `"ignored": true`). Save the time if you want to show "phone last seen" in your admin panel.

**Retries.** The same message can arrive more than once (for example when your answer was lost on the way back). Make the TrxID unique in your database and treat a repeat as success.

## What to answer

| Status | Body | What the app does |
| --- | --- | --- |
| `200` | `{ "ok": true, "result": "verified" }` | Marks it sent and shows the result next to the payment. |
| `200` | `{ "ok": true, "ignored": true }` | Marks it sent (not a payment message, or the heartbeat). |
| `401` or `403` | anything | Stops sending, keeps the queue, tells the owner the key is wrong. |
| `400` | `{ "ok": false, "error": "…" }` | This message will never be accepted: marks it "Not sent". |
| `503` | `{ "ok": false, "error": "not_configured" }` | Like 401: the server has no key yet. |
| other / timeout | | Tries again later. |

`result` values the app understands: `verified` (order confirmed), `no_order` (saved, no order quotes this TrxID yet), `mismatch` (amount or wallet differ from the order), `already_used` (this TrxID already paid an order). Anything else is shown as "Sent".

## Reading the message

The three wallets currently send these formats ("money received" only; the app does not forward sent money, cash out or codes):

| Wallet (`from`) | Example `text` |
| --- | --- |
| bKash (`bKash`) | `You have received Tk 1,490.00 from 01711111111. Fee Tk 0.00. Balance Tk 5,000.00. TrxID CKA1B2C3D4 at 24/11/2026 16:45` |
| bKash payment | `You have received payment Tk 500.00 from 01711111111. … TrxID CKA1B2C3D4 …` |
| Nagad (`NAGAD`) | `Money Received. Amount: Tk 500.00 Sender: 01711111111 Ref: N/A TxnID: 73ABCD12 Balance: Tk 1,000.00 24/11/2026 16:45` |
| Rocket (`16216`) | `Tk500.00 received from A/C:017111111118 Fee:Tk0.00, Your A/C Balance: Tk1,000.00 TxnId:1234567890 Date:24-NOV-26 04:45:00 pm.` |

Patterns that read amount, payer and TrxID (JavaScript syntax, the same in PHP and Python):

```js
const WALLETS = [
  { wallet: "bkash",  sender: /^bkash$/i,               amount: /received(?: payment)? Tk\s*([\d,]+(?:\.\d+)?)/i, payer: /from\s*(\+?\d{11,13})/i,    trx: /TrxID\s*:?\s*([A-Z0-9]{8,12})/i },
  { wallet: "nagad",  sender: /^nagad$/i,               amount: /Amount:\s*Tk\s*([\d,]+(?:\.\d+)?)/i,           payer: /Sender:\s*(\+?\d{11,13})/i,  trx: /TxnID:\s*([A-Z0-9]{6,12})/i },
  { wallet: "rocket", sender: /^(16216|rocket|dbbl)$/i, amount: /Tk\s*([\d,]+(?:\.\d+)?)\s*received from A\/C/i,  payer: /from A\/C:\s*([\d*]+)/i,     trx: /TxnId:\s*(\d{6,12})/i },
];
```

Notes:
- Check `from` too. It is the strongest proof the message really came from the wallet.
- Rocket's payer is the account number: the mobile number plus one digit.
- Wallets change their wording from time to time. If a message stops matching, it is logged by your server as ignored; [open an issue](https://github.com/SahinurDEV/pay-relay/issues/new/choose) with an example (made-up numbers).

## Matching it to an order

1. Save every payment message: wallet, amount, payer, TrxID (unique), time.
2. When a customer submits a TrxID for an order, look for a saved payment with that TrxID. When a payment arrives, look for a waiting order that quotes its TrxID. The two can arrive in either order.
3. Confirm the order only when the **wallet matches** and the **amount is at least the order total**, and mark the payment as used so the same TrxID can never pay twice.

## Node.js (Express)

```js
import express from "express";
import crypto from "node:crypto";

const app = express();
app.use(express.json({ limit: "16kb" }));
const KEY = process.env.PAY_RELAY_KEY;
const same = (a, b) => crypto.timingSafeEqual(crypto.createHash("sha256").update(a).digest(), crypto.createHash("sha256").update(b).digest());

app.post("/api/payments/sms", async (req, res) => {
  const given = (req.get("authorization") || "").replace(/^Bearer\s+/i, "");
  if (!KEY) return res.status(503).json({ ok: false, error: "not_configured" });
  if (!same(given, KEY)) return res.status(401).json({ ok: false, error: "unauthorized" });

  const { from = "", text = "" } = req.body || {};
  const w = WALLETS.find((w) => w.sender.test(from.trim()) && w.amount.test(text) && w.trx.test(text));
  if (!w) return res.json({ ok: true, ignored: true }); // heartbeat or not a payment

  const payment = {
    wallet: w.wallet,
    amount: Number(text.match(w.amount)[1].replace(/,/g, "")),
    payer: text.match(w.payer)?.[1] ?? "",
    trxId: text.match(w.trx)[1].toUpperCase(),
  };
  const result = await savePaymentAndMatchOrder(payment); // your code: "verified" | "no_order" | "mismatch" | "already_used"
  res.json({ ok: true, result });
});
```

## PHP

```php
<?php
// payments-sms.php
$key = getenv('PAY_RELAY_KEY');
$given = preg_replace('/^Bearer\s+/i', '', $_SERVER['HTTP_AUTHORIZATION'] ?? '');
header('Content-Type: application/json');
if (!$key) { http_response_code(503); exit(json_encode(['ok' => false, 'error' => 'not_configured'])); }
if (!hash_equals(hash('sha256', $key), hash('sha256', $given))) { http_response_code(401); exit(json_encode(['ok' => false])); }

$body = json_decode(file_get_contents('php://input'), true) ?: [];
$from = trim($body['from'] ?? '');
$text = $body['text'] ?? '';

$wallets = [
  'bkash'  => ['/^bkash$/i', '/received(?: payment)? Tk\s*([\d,]+(?:\.\d+)?)/i', '/from\s*(\+?\d{11,13})/i', '/TrxID\s*:?\s*([A-Z0-9]{8,12})/i'],
  'nagad'  => ['/^nagad$/i', '/Amount:\s*Tk\s*([\d,]+(?:\.\d+)?)/i', '/Sender:\s*(\+?\d{11,13})/i', '/TxnID:\s*([A-Z0-9]{6,12})/i'],
  'rocket' => ['/^(16216|rocket|dbbl)$/i', '/Tk\s*([\d,]+(?:\.\d+)?)\s*received from A\/C/i', '/from A\/C:\s*([\d*]+)/i', '/TxnId:\s*(\d{6,12})/i'],
];
foreach ($wallets as $wallet => [$sender, $amount, $payer, $trx]) {
  if (preg_match($sender, $from) && preg_match($amount, $text, $a) && preg_match($trx, $text, $t)) {
    preg_match($payer, $text, $p);
    $result = save_payment_and_match_order($wallet, (float) str_replace(',', '', $a[1]), $p[1] ?? '', strtoupper($t[1])); // your code
    exit(json_encode(['ok' => true, 'result' => $result]));
  }
}
echo json_encode(['ok' => true, 'ignored' => true]);
```

Apache may drop the `Authorization` header; if `$given` is always empty, add `SetEnvIf Authorization "(.*)" HTTP_AUTHORIZATION=$1` to `.htaccess`.

## Laravel

```php
// routes/api.php
Route::post('/payments/sms', PaymentSmsController::class)->middleware('throttle:120,1');

// app/Http/Controllers/PaymentSmsController.php
public function __invoke(Request $request)
{
    $key = config('services.pay_relay.key');
    abort_unless($key, 503);
    abort_unless(hash_equals(hash('sha256', $key), hash('sha256', (string) $request->bearerToken())), 401);

    $parsed = WalletSms::parse($request->string('from'), $request->string('text')); // the patterns above
    if (! $parsed) return ['ok' => true, 'ignored' => true];

    $payment = Payment::firstOrCreate(['trx_id' => $parsed['trx_id']], $parsed);
    return ['ok' => true, 'result' => $payment->matchOrder()]; // your code
}
```

## Python (Flask)

```python
import hashlib, hmac, os, re
from flask import Flask, request, jsonify

app = Flask(__name__)
KEY = os.environ.get("PAY_RELAY_KEY", "")
WALLETS = [
    ("bkash",  r"^bkash$",               r"received(?: payment)? Tk\s*([\d,]+(?:\.\d+)?)", r"from\s*(\+?\d{11,13})",   r"TrxID\s*:?\s*([A-Z0-9]{8,12})"),
    ("nagad",  r"^nagad$",               r"Amount:\s*Tk\s*([\d,]+(?:\.\d+)?)",            r"Sender:\s*(\+?\d{11,13})", r"TxnID:\s*([A-Z0-9]{6,12})"),
    ("rocket", r"^(16216|rocket|dbbl)$", r"Tk\s*([\d,]+(?:\.\d+)?)\s*received from A/C",   r"from A/C:\s*([\d*]+)",     r"TxnId:\s*(\d{6,12})"),
]

@app.post("/api/payments/sms")
def payment_sms():
    if not KEY:
        return jsonify(ok=False, error="not_configured"), 503
    given = re.sub(r"^Bearer\s+", "", request.headers.get("Authorization", ""), flags=re.I)
    if not hmac.compare_digest(hashlib.sha256(given.encode()).digest(), hashlib.sha256(KEY.encode()).digest()):
        return jsonify(ok=False), 401

    body = request.get_json(silent=True) or {}
    sender, text = body.get("from", "").strip(), body.get("text", "")
    for wallet, s, a, p, t in WALLETS:
        if re.match(s, sender, re.I) and (am := re.search(a, text, re.I)) and (tx := re.search(t, text, re.I)):
            payer = re.search(p, text, re.I)
            result = save_payment_and_match_order(wallet, float(am[1].replace(",", "")), payer[1] if payer else "", tx[1].upper())  # your code
            return jsonify(ok=True, result=result)
    return jsonify(ok=True, ignored=True)
```

## Testing without a phone

```bash
curl -i https://your-site.com/api/payments/sms \
  -H "Authorization: Bearer $PAY_RELAY_KEY" \
  -H "Content-Type: application/json" \
  -d '{"from":"bKash","text":"You have received Tk 10.00 from 01700000000. Fee Tk 0.00. Balance Tk 10.00. TrxID TEST123456 at 26/11/2026 10:00"}'
```

Expect `200` with a `result`. Try a wrong key (expect `401`) and `"from":"01700000000"` (expect `ignored`). Delete the test payment afterwards.

## Checklist

- [ ] HTTPS only, key at least 32 random characters, compared in constant time
- [ ] Sender (`from`) checked, not just the text
- [ ] TrxID unique in the database; a repeat answers `200`
- [ ] Order confirmed only when wallet and amount match, and each TrxID pays one order
- [ ] Rate limit on the endpoint (for example 120 requests a minute)
- [ ] Answers within a few seconds (do slow work like emails after answering)
