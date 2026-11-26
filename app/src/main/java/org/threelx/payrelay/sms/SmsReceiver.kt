package org.threelx.payrelay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.threelx.payrelay.PayRelayApp
import org.threelx.payrelay.work.Scheduler

/**
 * Called by Android for every incoming SMS. A message that is not from a
 * wallet is dropped right here: it is not stored, logged or sent anywhere.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return

        // A long SMS arrives in parts from the same sender.
        val sender = parts.first().displayOriginatingAddress ?: return
        if (Wallet.of(sender) == null) return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }

        val app = context.applicationContext as PayRelayApp
        val pending = goAsync()
        scope.launch {
            try {
                if (app.relay.accept(sender, body, System.currentTimeMillis())) Scheduler.sendNow(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
