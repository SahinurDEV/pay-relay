package org.threelx.payrelay.sms

import java.security.MessageDigest

/** The mobile wallets whose "money received" messages are passed on. */
enum class Wallet(val label: String, private val senderIds: Regex) {
    BKASH("bKash", Regex("^bkash$", RegexOption.IGNORE_CASE)),
    NAGAD("Nagad", Regex("^nagad$", RegexOption.IGNORE_CASE)),
    ROCKET("Rocket", Regex("^(16216|rocket|dbbl)$", RegexOption.IGNORE_CASE));

    companion object {
        /** The wallet that sends under this sender ID, or null for anyone else. */
        fun of(sender: String?): Wallet? {
            val id = sender?.trim().orEmpty()
            return entries.firstOrNull { it.senderIds.matches(id) }
        }
    }
}

/** What the app could read from a message, to show it in the list. The server does its own reading. */
data class PaymentDetails(val amount: Double?, val payer: String?, val trxId: String?)

/**
 * Decides which messages leave the phone.
 *
 * Only messages from a wallet's own sender ID are ever considered, so private
 * messages are never read further, stored or sent. One-time codes and PIN
 * messages are dropped even when they come from a wallet.
 */
object WalletFilter {
    private val secret = Regex(
        "\\b(otp|pin|password|passcode|verification code|security code|one[- ]time)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val received = Regex("\\breceived\\b", RegexOption.IGNORE_CASE)

    private val amountPatterns = listOf(
        Regex("received(?: payment)? Tk\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("Amount:\\s*Tk\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        Regex("Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*received", RegexOption.IGNORE_CASE),
    )
    private val payerPatterns = listOf(
        Regex("from\\s*(\\+?\\d{11,13})", RegexOption.IGNORE_CASE),
        Regex("Sender:\\s*(\\+?\\d{11,13})", RegexOption.IGNORE_CASE),
        Regex("from A/C:\\s*([\\d*]+)", RegexOption.IGNORE_CASE),
    )
    private val trxPattern = Regex("(?:TrxID|TxnID)\\s*:?\\s*([A-Z0-9]{6,12})", RegexOption.IGNORE_CASE)

    fun normalise(body: String): String = body.replace(Regex("\\s+"), " ").trim()

    fun looksSecret(body: String): Boolean = secret.containsMatchIn(body)

    fun looksReceived(body: String): Boolean = received.containsMatchIn(body)

    /**
     * @param receivedOnly pass on "money received" messages only (the default),
     *                     or every non-secret message the wallet sends.
     */
    fun shouldForward(sender: String?, body: String, receivedOnly: Boolean = true): Boolean {
        if (Wallet.of(sender) == null) return false
        val text = normalise(body)
        if (text.isEmpty() || text.length > 600) return false
        if (looksSecret(text)) return false
        return !receivedOnly || looksReceived(text)
    }

    fun details(body: String): PaymentDetails {
        val text = normalise(body)
        val amount = amountPatterns.firstNotNullOfOrNull { it.find(text) }
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val payer = payerPatterns.firstNotNullOfOrNull { it.find(text) }?.groupValues?.get(1)
        val trxId = trxPattern.find(text)?.groupValues?.get(1)?.uppercase()
        return PaymentDetails(amount, payer, trxId)
    }

    /** The same message always gives the same key, so it is never queued twice. */
    fun key(sender: String, body: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${sender.trim().lowercase()}|${normalise(body)}".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
