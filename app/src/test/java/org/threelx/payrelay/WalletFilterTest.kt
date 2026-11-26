package org.threelx.payrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threelx.payrelay.sms.Wallet
import org.threelx.payrelay.sms.WalletFilter

class WalletFilterTest {
    private val bkash = "You have received Tk 1,490.00 from 01711111111. Fee Tk 0.00. Balance Tk 5,000.00. TrxID CKA1B2C3D4 at 24/11/2026 16:45"
    private val nagad = "Money Received. Amount: Tk 500.00 Sender: 01711111111 Ref: N/A TxnID: 73ABCD12 Balance: Tk 1,000.00 24/11/2026 16:45"
    private val rocket = "Tk500.00 received from A/C:017111111118 Fee:Tk0.00, Your A/C Balance: Tk1,000.00 TxnId:1234567890 Date:24-NOV-26 04:45:00 pm."

    @Test
    fun `wallet sender ids are recognised whatever the letter case`() {
        assertEquals(Wallet.BKASH, Wallet.of("bKash"))
        assertEquals(Wallet.BKASH, Wallet.of(" BKASH "))
        assertEquals(Wallet.NAGAD, Wallet.of("NAGAD"))
        assertEquals(Wallet.ROCKET, Wallet.of("16216"))
        assertEquals(Wallet.ROCKET, Wallet.of("DBBL"))
    }

    @Test
    fun `anyone else is not a wallet`() {
        assertNull(Wallet.of("01711111111"))
        assertNull(Wallet.of("+8801711111111"))
        assertNull(Wallet.of("bKash-Offer"))
        assertNull(Wallet.of(null))
    }

    @Test
    fun `money received messages from the wallets are passed on`() {
        assertTrue(WalletFilter.shouldForward("bKash", bkash))
        assertTrue(WalletFilter.shouldForward("NAGAD", nagad))
        assertTrue(WalletFilter.shouldForward("16216", rocket))
    }

    @Test
    fun `the same text from a private number is never passed on`() {
        assertFalse(WalletFilter.shouldForward("01711111111", bkash))
        assertFalse(WalletFilter.shouldForward("+8801999888777", nagad))
    }

    @Test
    fun `codes and pin messages stay on the phone even from a wallet`() {
        assertFalse(WalletFilter.shouldForward("bKash", "Your bKash verification code is 123456. Never share it."))
        assertFalse(WalletFilter.shouldForward("bKash", "Your OTP is 4321 for the payment you received request."))
        assertFalse(WalletFilter.shouldForward("NAGAD", "Never share your PIN. You have received a bonus.", receivedOnly = false))
    }

    @Test
    fun `other wallet messages are passed on only when asked`() {
        val sent = "Send Money Tk 200.00 to 01711111111 successful. Fee Tk 5.00. Balance Tk 100.00. TrxID CKA9Z8Y7X6"
        assertFalse(WalletFilter.shouldForward("bKash", sent))
        assertTrue(WalletFilter.shouldForward("bKash", sent, receivedOnly = false))
    }

    @Test
    fun `empty and oversized messages are dropped`() {
        assertFalse(WalletFilter.shouldForward("bKash", "   "))
        assertFalse(WalletFilter.shouldForward("bKash", "received ".repeat(100)))
    }

    @Test
    fun `details are read from each wallet format`() {
        with(WalletFilter.details(bkash)) {
            assertEquals(1490.0, amount!!, 0.001)
            assertEquals("01711111111", payer)
            assertEquals("CKA1B2C3D4", trxId)
        }
        with(WalletFilter.details(nagad)) {
            assertEquals(500.0, amount!!, 0.001)
            assertEquals("01711111111", payer)
            assertEquals("73ABCD12", trxId)
        }
        with(WalletFilter.details(rocket)) {
            assertEquals(500.0, amount!!, 0.001)
            assertEquals("017111111118", payer)
            assertEquals("1234567890", trxId)
        }
    }

    @Test
    fun `a message with nothing to read gives empty details`() {
        with(WalletFilter.details("Welcome to the wallet.")) {
            assertNull(amount)
            assertNull(payer)
            assertNull(trxId)
        }
    }

    @Test
    fun `the key ignores spacing and sender case but not the text`() {
        assertEquals(WalletFilter.key("bKash", bkash), WalletFilter.key(" BKASH", bkash.replace(" ", "  ")))
        assertNotEquals(WalletFilter.key("bKash", bkash), WalletFilter.key("bKash", bkash.replace("CKA1B2C3D4", "CKA1B2C3D5")))
    }
}
