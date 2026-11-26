package org.threelx.payrelay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.threelx.payrelay.data.AppDatabase
import org.threelx.payrelay.data.Settings
import org.threelx.payrelay.net.GatewayClient
import org.threelx.payrelay.sms.Relay
import org.threelx.payrelay.work.Scheduler

class PayRelayApp : Application() {
    val settings by lazy { Settings(this) }
    val database by lazy { AppDatabase.create(this) }
    val gateway by lazy { GatewayClient() }
    val relay by lazy { Relay(this, settings, database.messages(), gateway) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROBLEMS, getString(R.string.channel_problems), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, getString(R.string.channel_work), NotificationManager.IMPORTANCE_LOW),
        )
        Scheduler.keepAlive(this)
    }

    companion object {
        const val CHANNEL_PROBLEMS = "problems"
        const val CHANNEL_WORK = "work"
    }
}
