package org.threelx.payrelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.threelx.payrelay.BuildConfig
import org.threelx.payrelay.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: MainViewModel, background: Boolean, onAllowBackground: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val autoStart = remember { AutoStart.intent(context) }
    val config by model.config.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf(config.url) }
    // The key is deliberately not kept across a screen rotation or shown once saved.
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }

    // Once the key is saved it has no reason to stay on screen.
    LaunchedEffect(notice) { if (notice?.good == true && config.hasKey) key = "" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.connection)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    placeholder = { Text(stringResource(R.string.server_address_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("url"),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.server_key)) },
                    supportingText = { Text(stringResource(if (config.hasKey) R.string.server_key_saved else R.string.server_key_hint)) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(stringResource(if (showKey) R.string.hide else R.string.show)) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("key"),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { model.saveConnection(url, key) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("save")) {
                    Text(stringResource(if (busy) R.string.testing else R.string.save_connect))
                }
                notice?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (note.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("notice"),
                    )
                    if (note.canSaveAnyway) {
                        TextButton(onClick = { model.saveConnection(url, key, force = true) }) { Text(stringResource(R.string.save_anyway)) }
                    }
                }
            }

            Section(stringResource(R.string.options)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.received_only), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.received_only_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = config.receivedOnly, onCheckedChange = model::setReceivedOnly)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = model::scanInbox, enabled = !busy && config.configured, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_inbox))
                }
                OutlinedButton(onClick = model::clearHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clear_history)) }
            }

            Section(stringResource(R.string.running)) {
                Text(stringResource(R.string.running_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (background) {
                    Text(stringResource(R.string.running_battery_ok), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = onAllowBackground, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.running_battery)) }
                }
                if (autoStart != null) {
                    OutlinedButton(onClick = { runCatching { context.startActivity(autoStart) } }, modifier = Modifier.fillMaxWidth().testTag("autostart")) {
                        Text(stringResource(R.string.running_autostart, AutoStart.maker))
                    }
                }
                OutlinedButton(onClick = { runCatching { context.startActivity(AutoStart.appDetails(context)) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.running_app_info))
                }
            }

            Section(stringResource(R.string.about)) {
                Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
