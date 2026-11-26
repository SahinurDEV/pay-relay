package org.threelx.payrelay.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Many phone makers stop apps from starting by themselves (after a restart,
 * or when a message arrives) unless the owner allows it on a screen of their
 * own. Android has no standard way to open it, so these are the known ones.
 */
object AutoStart {
    private val screens = listOf(
        // Xiaomi, Redmi, POCO (MIUI / HyperOS)
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Oppo, Realme, OnePlus (ColorOS / realme UI / OxygenOS)
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // Vivo, iQOO (Funtouch / OriginOS)
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        // Huawei, Honor (EMUI / MagicOS)
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // Samsung (One UI): the battery screen, where "Never sleeping apps" lives
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        // Asus
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        // Infinix, Tecno, itel (XOS / HiOS)
        ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
    )

    /** The maker's auto-start screen on this phone, or null when it has none we know. */
    fun intent(context: Context): Intent? = screens
        .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { candidate ->
            context.packageManager.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.exported == true
        }

    /** The app's own page in Android settings: battery, notifications and permissions are all one tap from here. */
    fun appDetails(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val maker: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}
