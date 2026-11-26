package org.threelx.payrelay.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.threelx.payrelay.Access
import org.threelx.payrelay.R
import org.threelx.payrelay.data.Config
import org.threelx.payrelay.data.Message
import org.threelx.payrelay.data.Status
import org.threelx.payrelay.sms.Wallet
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    model: MainViewModel,
    access: Access,
    onSettings: () -> Unit,
    onAllowSms: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowBackground: () -> Unit,
) {
    val config by model.config.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val waiting by model.waiting.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    config = config,
                    access = access,
                    onConnect = onSettings,
                    onAllowSms = onAllowSms,
                    onRetry = model::refresh,
                    onToggle = model::setEnabled,
                )
            }
            if (config.configured && access.sms) {
                if (!access.notifications) item { FixRow(stringResource(R.string.fix_notifications), onAllowNotifications) }
                if (!access.background) item { FixRow(stringResource(R.string.fix_battery), onAllowBackground) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val amount = today.amount?.let { stringResource(R.string.taka, money(it)) } ?: "—"
                        Figure(stringResource(R.string.today), amount, pluralStringResource(R.plurals.payments_count, today.count, today.count), Modifier.weight(1f))
                        Figure(stringResource(R.string.waiting), waiting.toString(), null, Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(messages, key = { it.id }) { message -> MessageRow(message) { openId = message.id } }
            }
        }
    }

    messages.firstOrNull { it.id == openId }?.let { message ->
        ModalBottomSheet(onDismissRequest = { openId = null }, containerColor = MaterialTheme.colorScheme.surface) {
            MessageSheet(message, onRetry = { model.retry(message.id); openId = null }, onClose = { openId = null })
        }
    }
}

private data class StatusView(val title: String, val hint: String, val back: Color, val front: Color, val action: String? = null, val onAction: () -> Unit = {})

@Composable
private fun StatusCard(
    config: Config,
    access: Access,
    onConnect: () -> Unit,
    onAllowSms: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val signals = LocalSignals.current
    val scheme = MaterialTheme.colorScheme

    val view = when {
        !config.configured -> StatusView(stringResource(R.string.status_setup), stringResource(R.string.status_setup_hint), signals.warn, signals.onWarn, stringResource(R.string.connect), onConnect)
        !access.sms -> StatusView(stringResource(R.string.status_permission), stringResource(R.string.status_permission_hint), signals.warn, signals.onWarn, stringResource(R.string.allow_sms), onAllowSms)
        !config.enabled -> StatusView(stringResource(R.string.status_paused), stringResource(R.string.status_paused_hint), scheme.surfaceVariant, scheme.onSurfaceVariant)
        config.lastError.isNotBlank() -> StatusView(stringResource(R.string.status_error), config.lastError, scheme.errorContainer, scheme.onErrorContainer, stringResource(R.string.try_again), onRetry)
        config.lastContactAt == 0L -> StatusView(stringResource(R.string.status_ok), stringResource(R.string.status_ok_never), signals.good, signals.onGood)
        else -> StatusView(stringResource(R.string.status_ok), stringResource(R.string.status_ok_hint, ago(config.lastContactAt)), signals.good, signals.onGood)
    }

    Card(colors = CardDefaults.cardColors(containerColor = view.back, contentColor = view.front), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(view.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("status"))
                    Spacer(Modifier.height(4.dp))
                    Text(view.hint, style = MaterialTheme.typography.bodyMedium)
                }
                if (config.configured && access.sms) {
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = config.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("enabled"))
                }
            }
            if (view.action != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = view.onAction, modifier = Modifier.testTag("status-action")) { Text(view.action) }
            }
        }
    }
}

@Composable
private fun FixRow(text: String, onAllow: () -> Unit) {
    val signals = LocalSignals.current
    Card(colors = CardDefaults.cardColors(containerColor = signals.warn, contentColor = signals.onWarn), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow), color = signals.onWarn, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note ?: " ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun walletColour(name: String): Color = when (name) {
    Wallet.BKASH.name -> LocalSignals.current.bkash
    Wallet.NAGAD.name -> LocalSignals.current.nagad
    else -> LocalSignals.current.rocket
}

private fun walletLabel(name: String) = Wallet.entries.firstOrNull { it.name == name }?.label ?: name

/** One line that says where the message stands, and whether that is good news. */
@Composable
private fun standing(message: Message): Pair<String, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (message.status) {
        Status.WAITING -> stringResource(R.string.state_waiting) to scheme.onSurfaceVariant
        Status.FAILED -> stringResource(R.string.state_failed) to scheme.error
        else -> when (message.serverResult) {
            "verified" -> stringResource(R.string.result_verified) to scheme.primary
            "no_order", "no_payment" -> stringResource(R.string.result_no_order) to scheme.onSurfaceVariant
            "duplicate", "already_used" -> stringResource(R.string.result_duplicate) to scheme.onSurfaceVariant
            "mismatch" -> stringResource(R.string.result_mismatch) to scheme.error
            "ignored" -> stringResource(R.string.result_ignored) to scheme.onSurfaceVariant
            else -> stringResource(R.string.result_sent) to scheme.onSurfaceVariant
        }
    }
}

@Composable
private fun MessageRow(message: Message, onClick: () -> Unit) {
    val (state, colour) = standing(message)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag("message"),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(walletColour(message.wallet), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.amount?.let { stringResource(R.string.taka, money(it)) } ?: walletLabel(message.wallet),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(ago(message.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val who = listOfNotNull(
                    walletLabel(message.wallet).takeIf { message.amount != null },
                    message.payer?.let { stringResource(R.string.from_payer, it) },
                    message.trxId,
                ).joinToString(" · ")
                if (who.isNotEmpty()) {
                    Text(who, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(state, style = MaterialTheme.typography.bodySmall, color = colour, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MessageSheet(message: Message, onRetry: () -> Unit, onClose: () -> Unit) {
    val (state, colour) = standing(message)
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Text(
            message.amount?.let { stringResource(R.string.taka, money(it)) } ?: walletLabel(message.wallet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(state, style = MaterialTheme.typography.bodyMedium, color = colour, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        message.trxId?.let { Detail(stringResource(R.string.trx_id), it, mono = true) }
        Detail(stringResource(R.string.received_at), "${walletLabel(message.wallet)} · ${dateTime(message.receivedAt)}")
        message.sentAt?.let { Detail(stringResource(R.string.sent_at), dateTime(it)) }
        if (message.attempts > 1) Detail(stringResource(R.string.attempts), message.attempts.toString())
        message.error?.let { Detail(stringResource(R.string.problem), it) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.message_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        SelectionContainer { Text(message.body, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.status == Status.FAILED) Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else null)
        }
    }
}

private fun money(amount: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(amount)

@Composable
private fun ago(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.just_now)
    return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

private fun dateTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
