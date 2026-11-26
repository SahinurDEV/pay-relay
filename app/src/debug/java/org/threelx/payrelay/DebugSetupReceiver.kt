package org.threelx.payrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Debug builds only (this file is not part of a release). Lets a developer
 * connect the app, or feed it a message, from the computer:
 *
 *   adb shell am broadcast -n org.threelx.payrelay.debug/org.threelx.payrelay.DebugSetupReceiver \
 *       --es url http://10.0.2.2:3000/api/payments/sms --es key test-key
 *   adb shell am broadcast -n org.threelx.payrelay.debug/org.threelx.payrelay.DebugSetupReceiver \
 *       --es from bKash --es text "You have received Tk 10.00 from 01700000000. TrxID TEST123456"
 */
class DebugSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PayRelayApp
        val url = intent.getStringExtra("url")
        if (url != null) app.settings.saveConnection(url, intent.getStringExtra("key"))
        val text = intent.getStringExtra("text")
        if (text != null) {
            runBlocking { app.relay.accept(intent.getStringExtra("from") ?: "bKash", text, System.currentTimeMillis()) }
            org.threelx.payrelay.work.Scheduler.sendNow(app)
        }
    }
}
