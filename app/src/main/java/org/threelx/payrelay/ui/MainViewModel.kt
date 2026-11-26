package org.threelx.payrelay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.threelx.payrelay.BuildConfig
import org.threelx.payrelay.PayRelayApp
import org.threelx.payrelay.R
import org.threelx.payrelay.data.DayTotals
import org.threelx.payrelay.data.Message
import org.threelx.payrelay.net.Reply
import org.threelx.payrelay.work.Scheduler
import java.util.Calendar

/** What the settings screen shows under the buttons. */
data class Notice(val text: String, val good: Boolean, val canSaveAnyway: Boolean = false)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PayRelayApp
    private val dao = app.database.messages()

    val config = app.settings.config
    val messages: StateFlow<List<Message>> = dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val waiting: StateFlow<Int> = dao.waitingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val today: StateFlow<DayTotals> = dao.totalsSince(startOfToday()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayTotals(0, null))

    val busy = MutableStateFlow(false)
    val notice = MutableStateFlow<Notice?>(null)

    private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

    fun setEnabled(enabled: Boolean) {
        app.settings.setEnabled(enabled)
        if (enabled) refresh()
    }

    fun setReceivedOnly(value: Boolean) = app.settings.setReceivedOnly(value)

    /** Called when the app comes to the front, and by "Try again". */
    fun refresh() {
        viewModelScope.launch {
            app.relay.catchUp()
            app.relay.heartbeat()
            Scheduler.sendNow(app)
        }
    }

    fun retry(id: Long? = null) {
        viewModelScope.launch { app.relay.retryFailed(id) }
    }

    /** Tests the address and key, and saves them when the store answers (or when [force] is set). */
    fun saveConnection(url: String, key: String, force: Boolean = false) {
        val address = url.trim()
        val problem = when {
            address.isEmpty() -> R.string.need_address
            !address.startsWith("https://") && !(BuildConfig.DEBUG && address.startsWith("http://")) -> R.string.need_https
            key.isBlank() && !config.value.hasKey -> R.string.need_key
            else -> null
        }
        if (problem != null) {
            notice.value = Notice(text(problem), good = false)
            return
        }
        viewModelScope.launch {
            busy.value = true
            notice.value = null
            if (force) {
                app.settings.saveConnection(address, key)
                notice.value = Notice(text(R.string.saved), good = true)
            } else {
                when (val reply = app.relay.test(address, key)) {
                    is Reply.Accepted -> {
                        app.settings.saveConnection(address, key)
                        app.settings.contactOk()
                        notice.value = Notice(text(R.string.test_ok), good = true)
                        app.relay.retryFailed()
                    }
                    is Reply.Refused -> notice.value = Notice(reply.reason, good = false)
                    is Reply.Rejected -> notice.value = Notice(reply.reason, good = false)
                    is Reply.Unreachable -> notice.value = Notice(reply.reason, good = false, canSaveAnyway = true)
                }
            }
            busy.value = false
        }
    }

    /** The saved key in clear, for the owner's own eyes on this phone. */
    fun savedKey(): String? = app.settings.key()

    /** Checks the saved address and key again. */
    fun testConnection() {
        val current = config.value
        if (!current.configured) return
        viewModelScope.launch {
            busy.value = true
            notice.value = null
            when (val reply = app.relay.test(current.url, null)) {
                is Reply.Accepted -> {
                    app.settings.contactOk()
                    notice.value = Notice(text(R.string.test_ok), good = true)
                    app.relay.retryFailed()
                }
                is Reply.Refused -> { app.settings.contactFailed(reply.reason); notice.value = Notice(reply.reason, good = false) }
                is Reply.Rejected -> { app.settings.contactFailed(reply.reason); notice.value = Notice(reply.reason, good = false) }
                is Reply.Unreachable -> { app.settings.contactFailed(reply.reason); notice.value = Notice(reply.reason, good = false) }
            }
            busy.value = false
        }
    }

    fun scanInbox() {
        viewModelScope.launch {
            busy.value = true
            val found = app.relay.catchUp()
            if (found > 0) Scheduler.sendNow(app)
            notice.value = Notice(app.resources.getQuantityString(R.plurals.scan_result, found, found), good = true)
            busy.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
            notice.value = Notice(text(R.string.cleared), good = true)
        }
    }

    fun clearNotice() {
        notice.value = null
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
