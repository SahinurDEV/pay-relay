package org.threelx.payrelay.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.threelx.payrelay.PayRelayApp
import org.threelx.payrelay.R
import org.threelx.payrelay.sms.Outcome
import java.util.concurrent.TimeUnit

/** Sends the waiting messages. Android runs it as soon as there is a connection, and again after a failure. */
class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PayRelayApp
        return when (app.relay.flush()) {
            Outcome.DONE, Outcome.STOPPED -> Result.success()
            Outcome.TRY_LATER -> Result.retry()
        }
    }

    // Only used before Android 12, where urgent work runs as a short foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, PayRelayApp.CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(applicationContext.getString(R.string.sending))
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(WORK_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(WORK_NOTIFICATION, notification)
        }
    }

    private companion object {
        const val WORK_NOTIFICATION = 2
    }
}

/**
 * Every 15 minutes: tell the server the phone is online, pick up anything
 * that was missed, and push the queue again.
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PayRelayApp
        app.relay.catchUp()
        app.relay.heartbeat()
        Scheduler.sendNow(app)
        return Result.success()
    }
}

object Scheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sendNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(online)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        // REPLACE starts a fresh round (and a fresh retry timer); a round that is cut short loses nothing,
        // because a message leaves the queue only after the server has taken it.
        WorkManager.getInstance(context).enqueueUniqueWork("send", ExistingWorkPolicy.REPLACE, request)
    }

    fun keepAlive(context: Context) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("heartbeat", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** After a restart or an update, pick up where things were left. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Scheduler.keepAlive(context)
        Scheduler.sendNow(context)
    }
}
