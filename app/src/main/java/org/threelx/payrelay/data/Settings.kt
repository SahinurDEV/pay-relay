package org.threelx.payrelay.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything the owner sets, plus the last known state of the connection. */
data class Config(
    val url: String = "",
    val hasKey: Boolean = false,
    val enabled: Boolean = true,
    /** Pass on "money received" messages only; off sends every non-secret wallet message. */
    val receivedOnly: Boolean = true,
    /** Messages that arrived before this moment are never picked up from the inbox. */
    val since: Long = 0,
    val lastContactAt: Long = 0,
    /** Empty when the last contact went well. */
    val lastError: String = "",
) {
    val configured: Boolean get() = url.isNotBlank() && hasKey
}

/**
 * Plain settings live in private SharedPreferences. The server key is
 * encrypted with a key that never leaves the phone's Android Keystore.
 */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val config: StateFlow<Config> = state

    private fun read() = Config(
        url = prefs.getString(URL, "").orEmpty(),
        hasKey = prefs.contains(KEY),
        enabled = prefs.getBoolean(ENABLED, true),
        receivedOnly = prefs.getBoolean(RECEIVED_ONLY, true),
        since = prefs.getLong(SINCE, 0),
        lastContactAt = prefs.getLong(LAST_CONTACT, 0),
        lastError = prefs.getString(LAST_ERROR, "").orEmpty(),
    )

    private fun change(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        state.value = read()
    }

    /** Saves the address, and the key when a new one was typed. */
    fun saveConnection(url: String, key: String?) = change {
        putString(URL, url.trim())
        if (!key.isNullOrBlank()) putString(KEY, encrypt(key.trim()))
        if (!prefs.contains(SINCE)) putLong(SINCE, System.currentTimeMillis())
        remove(LAST_ERROR)
    }

    fun setEnabled(enabled: Boolean) = change { putBoolean(ENABLED, enabled) }

    fun setReceivedOnly(value: Boolean) = change { putBoolean(RECEIVED_ONLY, value) }

    fun contactOk() = change {
        putLong(LAST_CONTACT, System.currentTimeMillis())
        remove(LAST_ERROR)
    }

    fun contactFailed(error: String) = change { putString(LAST_ERROR, error) }

    /** The key in clear, for the request header only. Null when none is saved or it cannot be read. */
    fun key(): String? = prefs.getString(KEY, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, sealed) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(sealed))
    }

    private companion object {
        const val URL = "url"
        const val KEY = "key"
        const val ENABLED = "enabled"
        const val RECEIVED_ONLY = "receivedOnly"
        const val SINCE = "since"
        const val LAST_CONTACT = "lastContactAt"
        const val LAST_ERROR = "lastError"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "payrelay-server-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
