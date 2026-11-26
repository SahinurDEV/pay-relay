package org.threelx.payrelay.sms

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.threelx.payrelay.MainActivity
import org.threelx.payrelay.PayRelayApp
import org.threelx.payrelay.R
import org.threelx.payrelay.data.Message
import org.threelx.payrelay.data.MessageDao
import org.threelx.payrelay.data.Settings
import org.threelx.payrelay.data.Status
import org.threelx.payrelay.net.GatewayClient
import org.threelx.payrelay.net.Reply
import org.threelx.payrelay.work.Scheduler

/** What a round of sending came to. */
enum class Outcome { DONE, TRY_LATER, STOPPED }

/**
 * The whole job in one place: take a wallet message in, keep it until the
 * server has it, and say so when something is in the way.
 */
class Relay(
    private val context: Context,
    private val settings: Settings,
    private val messages: MessageDao,
    private val gateway: GatewayClient,
) {
    private val sending = Mutex()

    /** Queues a message if it is one to pass on. Returns true when it was new. */
    suspend fun accept(sender: String, body: String, receivedAt: Long): Boolean {
        val config = settings.config.value
        if (!config.enabled || !WalletFilter.shouldForward(sender, body, config.receivedOnly)) return false
        val wallet = Wallet.of(sender) ?: return false
        val text = WalletFilter.normalise(body)
        val details = WalletFilter.details(text)
        val id = messages.insert(
            Message(
                key = WalletFilter.key(sender, text),
                sender = sender.trim(),
                wallet = wallet.name,
                body = text,
                amount = details.amount,
                payer = details.payer,
                trxId = details.trxId,
                receivedAt = receivedAt,
            ),
        )
        return id != -1L
    }

    /**
     * Picks up wallet messages that are in the phone's inbox but were never
     * queued, for example because the app was force-stopped when they arrived.
     * Only looks at messages that came after the app was connected.
     */
    suspend fun catchUp(): Int = withContext(Dispatchers.IO) {
        val config = settings.config.value
        if (!config.enabled || !config.configured) return@withContext 0
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return@withContext 0
        val since = maxOf(config.since, System.currentTimeMillis() - CATCH_UP_WINDOW)
        var added = 0
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(0) ?: continue
                    val body = cursor.getString(1) ?: continue
                    if (accept(sender, body, cursor.getLong(2))) added += 1
                }
            }
        }
        added
    }

    /** Sends everything that is waiting, oldest first. */
    suspend fun flush(): Outcome = sending.withLock {
        val config = settings.config.value
        val key = settings.key()
        if (!config.configured || key == null) return Outcome.STOPPED

        while (true) {
            val batch = messages.waiting()
            if (batch.isEmpty()) break
            for (message in batch) {
                when (val reply = gateway.send(config.url, key, message.sender, message.body, message.receivedAt)) {
                    is Reply.Accepted -> {
                        messages.markSent(message.id, reply.result, System.currentTimeMillis())
                        settings.contactOk()
                        clearProblem()
                    }
                    is Reply.Rejected -> messages.markProblem(message.id, Status.FAILED, reply.reason)
                    is Reply.Refused -> {
                        // Nothing will get through until the address or key is fixed: keep the queue and say so.
                        settings.contactFailed(reply.reason)
                        showProblem(reply.reason)
                        return Outcome.STOPPED
                    }
                    is Reply.Unreachable -> {
                        messages.markProblem(message.id, Status.WAITING, reply.reason)
                        settings.contactFailed(reply.reason)
                        return Outcome.TRY_LATER
                    }
                }
            }
        }
        messages.prune(System.currentTimeMillis() - KEEP_HISTORY)
        Outcome.DONE
    }

    /** Tells the server the phone is online, so the store's dashboard can show it. */
    suspend fun heartbeat(): Reply? {
        val config = settings.config.value
        val key = settings.key()
        if (!config.enabled || !config.configured || key == null) return null
        return gateway.ping(config.url, key).also { reply ->
            when (reply) {
                is Reply.Accepted -> { settings.contactOk(); clearProblem() }
                is Reply.Refused -> { settings.contactFailed(reply.reason); showProblem(reply.reason) }
                is Reply.Rejected -> settings.contactFailed(reply.reason)
                is Reply.Unreachable -> settings.contactFailed(reply.reason)
            }
        }
    }

    /** Checks an address and key before they are saved. */
    suspend fun test(url: String, key: String?): Reply {
        val use = key?.takeIf { it.isNotBlank() } ?: settings.key() ?: return Reply.Refused("Enter the key first.")
        return gateway.ping(url.trim(), use.trim())
    }

    suspend fun retryFailed(id: Long? = null) {
        messages.retry(id)
        Scheduler.sendNow(context)
    }

    private fun showProblem(reason: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, PayRelayApp.CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(context.getString(R.string.problem_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(PROBLEM_ID, notification)
    }

    private fun clearProblem() = context.getSystemService(NotificationManager::class.java).cancel(PROBLEM_ID)

    private companion object {
        const val PROBLEM_ID = 1
        const val CATCH_UP_WINDOW = 48L * 60 * 60 * 1000
        const val KEEP_HISTORY = 30L * 24 * 60 * 60 * 1000
    }
}
