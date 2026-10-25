package com.coeric.universalwebcontrol.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
    private val apiBase = ApiConstants.API_BASE_URL

    fun isConfigured(): Boolean =
        CloudflareOAuthConfig.CLIENT_ID.isNotBlank() &&
            !CloudflareOAuthConfig.CLIENT_ID.startsWith("REPLACE_")

    fun isConnected(): Boolean =
        secureStore.get(ApiConstants.STORE_KEY)?.isNotBlank() == true

    fun session(): CloudflareSession? {
        if (!isConnected()) return null
        return CloudflareSession(
            subject = secureStore.get("cloudflare_token_id"),
            email = null,
            name = "Cloudflare OAuth session",
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
                            "Cloudflare rejected the API token ($response.code). ${apiError(response.body)}"
                        )
                    )
                }

                val json = JSONObject(response.body)
                if (!json.optBoolean("success", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare rejected the API token. ${apiError(response.body)}")
                    )
                }

                val tokenId = json.optJSONObject("result")?.optString("id").orEmpty()
                val status = json.optJSONObject("result")?.optString("status").orEmpty()
                if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare API token status is $status.")
                    )
                }

                secureStore.put(ApiConstants.STORE_KEY, normalized)
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
            val token = secureStore.get(ApiConstants.STORE_KEY)
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
                            "Stored Cloudflare API token is no longer valid. ${apiError(response.body)}"
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
            val token = secureStore.get(ApiConstants.STORE_KEY)
                ?: return@withContext Result.failure(
                    IllegalStateException("Connect Cloudflare first.")
                )
            try {
                val response = getAuthorized("/zones?per_page=100", token)
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare zones request failed ($response.code). ${apiError(response.body)}")
                    )
                }
                val json = JSONObject(response.body)
                if (!json.optBoolean("success", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare zones request failed. ${apiError(response.body)}")
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

    fun beginAuthorization(): Result<Unit> = runCatching {
        require(
            CloudflareOAuthConfig.CLIENT_ID.isNotBlank() &&
                !CloudflareOAuthConfig.CLIENT_ID.startsWith("REPLACE_")
        ) { "Add your Cloudflare OAuth client ID in CloudflareOAuthConfig.kt before connecting." }

        val random = SecureRandom()
        fun randomUrlToken(bytes: Int): String {
            val value = ByteArray(bytes)
            random.nextBytes(value)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
        }

        val state = randomUrlToken(24)
        val verifier = randomUrlToken(32)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        secureStore.put("oauth_state", state)
        secureStore.put("oauth_verifier", verifier)

        val uri = Uri.parse(CloudflareOAuthConfig.AUTHORIZATION_ENDPOINT).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CloudflareOAuthConfig.CLIENT_ID)
            .appendQueryParameter("redirect_uri", CloudflareOAuthConfig.REDIRECT_URI)
            .appendQueryParameter("scope", CloudflareOAuthConfig.SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun completeAuthorization(callback: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                callback.getQueryParameter("error")?.let { error ->
                    throw IllegalStateException(
                        callback.getQueryParameter("error_description") ?: "Cloudflare authorization failed: $error"
                    )
                }
                val code = callback.getQueryParameter("code")
                    ?: throw IllegalStateException("Cloudflare did not return an authorization code.")
                val returnedState = callback.getQueryParameter("state")
                    ?: throw IllegalStateException("Cloudflare authorization state is missing.")
                val expectedState = secureStore.get("oauth_state")
                    ?: throw IllegalStateException("Authorization expired. Please connect again.")
                require(returnedState == expectedState) { "Authorization state did not match. Please try again." }
                val verifier = secureStore.get("oauth_verifier")
                    ?: throw IllegalStateException("The secure authorization verifier is missing. Please connect again.")

                fun form(value: String): String =
                    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                val body = listOf(
                    "grant_type" to "authorization_code",
                    "client_id" to CloudflareOAuthConfig.CLIENT_ID,
                    "code" to code,
                    "redirect_uri" to CloudflareOAuthConfig.REDIRECT_URI,
                    "code_verifier" to verifier
                ).joinToString("&") { (key, value) -> "${form(key)}=${form(value)}" }

                val connection = (URL(CloudflareOAuthConfig.TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                val response = readResponse(connection)
                val json = runCatching { JSONObject(response.body) }.getOrNull()
                val accessToken = json?.optString("access_token").orEmpty()
                if (response.code !in 200..299 || accessToken.isBlank()) {
                    val message = json?.optString("error_description")
                        ?.takeIf { it.isNotBlank() }
                        ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Cloudflare token exchange failed (HTTP ${response.code})."
                    throw IllegalStateException(message)
                }
                secureStore.put(ApiConstants.STORE_KEY, accessToken)
                json?.optString("refresh_token")?.takeIf { it.isNotBlank() }?.let {
                    secureStore.put("cloudflare_refresh_token", it)
                }
                secureStore.remove("oauth_state")
                secureStore.remove("oauth_verifier")
                Unit
            }
        }

    fun disconnectLocally() {
        secureStore.remove(ApiConstants.STORE_KEY)
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

    private object ApiConstants {
        const val API_BASE_URL = "https://api.cloudflare.com/client/v4/"
        const val STORE_KEY = "cf_session"
    }
}
