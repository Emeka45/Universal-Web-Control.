package com.coeric.universalwebcontrol.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CloudflareSession(
    val subject: String?,
    val email: String?,
    val name: String?,
    val expiresAtMillis: Long?
)

data class CloudflareZone(
    val id: String,
    val name: String,
    val status: String?,
    val planName: String?
)

class CloudflareOAuthManager(private val context: Context) {
    private val secureStore = SecureTokenStore(context)
    private val apiBase = CloudflareOAuthConfig.API_BASE_URL

    fun isConfigured(): Boolean = true

    fun isConnected(): Boolean =
        secureStore.get(CloudflareOAuthConfig.TOKEN_STORE_KEY)?.isNotBlank() == true

    fun session(): CloudflareSession? {
        if (!isConnected()) return null
        return CloudflareSession(
            subject = secureStore.get("cloudflare_token_id"),
            email = null,
            name = "Cloudflare API token",
            expiresAtMillis = null
        )
    }

    suspend fun connectWithApiToken(token: String): Result<CloudflareSession> =
        withContext(Dispatchers.IO) {
            val normalized = token.trim()
            if (normalized.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter a Cloudflare API token.")
                )
            }

            try {
                val response = getAuthorized("/user/tokens/verify", normalized)
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Cloudflare rejected the API token ($response.code). $apiError(response.body)"
                        )
                    )
                }

                val json = JSONObject(response.body)
                if (!json.optBoolean("success", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare rejected the API token. $apiError(response.body)")
                    )
                }

                val tokenId = json.optJSONObject("result")?.optString("id").orEmpty()
                val status = json.optJSONObject("result")?.optString("status").orEmpty()
                if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare API token status is $status.")
                    )
                }

                secureStore.put(CloudflareOAuthConfig.TOKEN_STORE_KEY, normalized)
                if (tokenId.isNotBlank()) {
                    secureStore.put("cloudflare_token_id", tokenId)
                }

                Result.success(session() ?: error("Cloudflare session was not created"))
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        "Could not reach Cloudflare. Check your internet connection and try again.",
                        e
                    )
                )
            }
        }

    suspend fun verifyStoredToken(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = secureStore.get(CloudflareOAuthConfig.TOKEN_STORE_KEY)
                ?: return@withContext Result.failure(
                    IllegalStateException("No Cloudflare API token is stored.")
                )
            try {
                val response = getAuthorized("/user/tokens/verify", token)
                if (response.code in 200..299 &&
                    JSONObject(response.body).optBoolean("success", false)
                ) {
                    Result.success(Unit)
                } else {
                    disconnectLocally()
                    Result.failure(
                        IllegalStateException(
                            "Stored Cloudflare API token is no longer valid. $apiError(response.body)"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException("Unable to verify the stored Cloudflare token.", e)
                )
            }
        }

    suspend fun listZones(): Result<List<CloudflareZone>> =
        withContext(Dispatchers.IO) {
            val token = secureStore.get(CloudflareOAuthConfig.TOKEN_STORE_KEY)
                ?: return@withContext Result.failure(
                    IllegalStateException("Connect Cloudflare first.")
                )
            try {
                val response = getAuthorized("/zones?per_page=100", token)
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare zones request failed ($response.code). $apiError(response.body)")
                    )
                }
                val json = JSONObject(response.body)
                if (!json.optBoolean("success", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare zones request failed. $apiError(response.body)")
                    )
                }
                val items = json.optJSONArray("result") ?: JSONArray()
                val zones = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        add(
                            CloudflareZone(
                                id = item.optString("id"),
                                name = item.optString("name"),
                                status = item.optString("status").takeIf { it.isNotBlank() },
                                planName = item.optJSONObject("plan")?.optString("name")
                                    ?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                Result.success(zones)
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException("Unable to read Cloudflare zones.", e)
                )
            }
        }

    fun disconnectLocally() {
        secureStore.remove(CloudflareOAuthConfig.TOKEN_STORE_KEY)
        secureStore.remove("cloudflare_token_id")
    }

    suspend fun revoke(): Result<Unit> {
        disconnectLocally()
        return Result.success(Unit)
    }

    private fun getAuthorized(path: String, token: String): HttpResponse {
        val connection = (URL(apiBase.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Authorization", authorizationHeader(token))
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiError(body: String): String {
        return runCatching {
            val errors = JSONObject(body).optJSONArray("errors") ?: return@runCatching ""
            buildList {
                for (index in 0 until errors.length()) {
                    errors.optJSONObject(index)?.optString("message")
                        ?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString("; ")
        }.getOrDefault("")
    }

    companion object {
        fun authorizationHeader(token: String): String {
            require(token.isNotBlank()) { "Cloudflare API token must not be blank." }
            return "Bearer ${token.trim()}"
        }
    }

    private data class HttpResponse(val code: Int, val body: String)
}
