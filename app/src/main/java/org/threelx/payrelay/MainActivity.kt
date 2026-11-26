package org.threelx.payrelay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.threelx.payrelay.ui.HomeScreen
import org.threelx.payrelay.ui.MainViewModel
import org.threelx.payrelay.ui.PayRelayTheme
import org.threelx.payrelay.ui.SettingsScreen

/** What Android currently lets the app do. Read again every time the app comes to the front. */
data class Access(val sms: Boolean, val notifications: Boolean, val background: Boolean)

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.access() = Access(
    sms = granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.READ_SMS),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
    background = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PayRelayTheme {
                val model: MainViewModel = viewModel()
                var access by remember { mutableStateOf(access()) }
                var onSettings by rememberSaveable { mutableStateOf(false) }

                LifecycleResumeEffect(Unit) {
                    access = access()
                    model.refresh()
                    onPauseOrDispose { }
                }

                val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    access = access()
                    model.refresh()
                }
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    access = access()
                }

                BackHandler(enabled = onSettings) {
                    model.clearNotice()
                    onSettings = false
                }

                if (onSettings) {
                    SettingsScreen(
                        model = model,
                        background = access.background,
                        onAllowBackground = ::askToRunInBackground,
                        onBack = { model.clearNotice(); onSettings = false },
                    )
                } else {
                    HomeScreen(
                        model = model,
                        access = access,
                        onSettings = { onSettings = true },
                        onAllowSms = { askSms.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) },
                        onAllowNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onAllowBackground = ::askToRunInBackground,
                    )
                }
            }
        }
    }

    // The app's whole job is to pass payments on the moment they arrive, also when the phone is idle.
    @SuppressLint("BatteryLife")
    private fun askToRunInBackground() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
        runCatching { startActivity(direct) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }
}
