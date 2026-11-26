package org.threelx.payrelay.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.threelx.payrelay.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

/** How one request to the server ended. */
sealed interface Reply {
    /** The server took the message. [result] is its word on it: verified, no_order, duplicate, ignored… */
    data class Accepted(val result: String) : Reply

    /** The key is wrong, or the server has no key set. Trying again will not help until that is fixed. */
    data class Refused(val reason: String) : Reply

    /** The server will never take this message (HTTP 400). */
    data class Rejected(val reason: String) : Reply

    /** No connection, a timeout or a server error: worth trying again later. */
    data class Unreachable(val reason: String) : Reply
}

/**
 * Talks to the store's SMS webhook:
 * `POST <url>` with `Authorization: Bearer <key>` and a JSON body `{ "from", "text" }`.
 */
class GatewayClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun send(url: String, key: String, from: String, text: String, receivedAt: Long? = null): Reply =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("from", from).put("text", text)
            if (receivedAt != null) body.put("receivedAt", receivedAt)
            val request = try {
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("User-Agent", "PayRelay/${BuildConfig.VERSION_NAME} (Android)")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            } catch (_: IllegalArgumentException) {
                return@withContext Reply.Refused("The server address is not a valid web address.")
            }
            try {
                http.newCall(request).execute().use { response ->
                    val json = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    val error = json?.optString("error").orEmpty()
                    when (response.code) {
                        in 200..299 -> Reply.Accepted(
                            when {
                                json == null -> "sent"
                                json.optBoolean("ignored") -> "ignored"
                                // A message the store already had (for example a retry after a lost answer) still
                                // reports how its order stands, which is what the owner wants to see.
                                json.has("result") -> json.optString("result")
                                json.optBoolean("duplicate") -> "duplicate"
                                else -> "sent"
                            },
                        )
                        401, 403 -> Reply.Refused("The server did not accept the key.")
                        503 -> if (error == "not_configured") Reply.Refused("The server has no SMS key set yet.") else Reply.Unreachable("Server busy (503)")
                        400 -> Reply.Rejected(error.ifEmpty { "Bad request (400)" })
                        404 -> Reply.Refused("Nothing answers at this address (404). Check the server address.")
                        else -> Reply.Unreachable("Server answered ${response.code}")
                    }
                }
            } catch (error: IOException) {
                Reply.Unreachable(error.message ?: "No connection")
            }
        }

    /** A harmless message the server acknowledges and ignores. Shows the address and key work. */
    suspend fun ping(url: String, key: String): Reply = send(url, key, from = "PayRelay", text = "ping")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
