# Changelog

## 1.0.0 (2026-11-26)

First release.

- Passes bKash, Nagad and Rocket "money received" SMS to your store's server over HTTPS.
- Reads only the wallets' own sender IDs; drops OTP, PIN and code messages.
- Queue on the phone with retry and back-off; nothing is lost without internet.
- Picks up missed messages from the inbox after a restart or a force stop.
- Heartbeat every 15 minutes so the store can show the phone is online.
- Status card, today's total, per-payment result from the store, notification when the store cannot be reached.
- Key encrypted with the Android Keystore; no backups, no analytics.
- English and Bangla.
