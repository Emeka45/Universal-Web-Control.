package com.coeric.universalwebcontrol.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class CloudflareOAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("cloudflare_oauth_flow", Context.MODE_PRIVATE)
    private val secureStore = SecureTokenStore(context)

    fun isConfigured(): Boolean =
        CloudflareOAuthConfig.CLIENT_ID.isNotBlank() &&
            CloudflareOAuthConfig.CLIENT_ID != "REPLACE_WITH_CLOUDFLARE_CLIENT_ID"

    fun isConnected(): Boolean = secureStore.get("access_token") != null

    fun session(): CloudflareSession? {
        if (!isConnected()) return null
        return CloudflareSession(
            subject = secureStore.get("subject"),
            email = secureStore.get("email"),
            name = secureStore.get("name"),
            expiresAtMillis = secureStore.get("expires_at")?.toLongOrNull()
        )
    }

    fun beginAuthorization(): Result<Unit> {
        if (!isConfigured()) {
            return Result.failure(
                IllegalStateException("Cloudflare OAuth is not configured. Add the Cloudflare OAuth client ID.")
            )
        }

        val verifier = randomUrlSafe(64)
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        val state = randomUrlSafe(32)

        prefs.edit()
            .putString("pkce_verifier", verifier)
            .putString("oauth_state", state)
            .apply()

        val uri = Uri.parse(CloudflareOAuthConfig.AUTHORIZATION_ENDPOINT)
            .buildUpon()
            .appendQueryParameter("client_id", CloudflareOAuthConfig.CLIENT_ID)
            .appendQueryParameter("redirect_uri", CloudflareOAuthConfig.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", CloudflareOAuthConfig.SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return Result.success(Unit)
    }

    suspend fun completeAuthorization(callback: Uri): Result<CloudflareSession> =
        withContext(Dispatchers.IO) {
            val expectedState = prefs.getString("oauth_state", null)
            val verifier = prefs.getString("pkce_verifier", null)
            val code = callback.getQueryParameter("code")
            val returnedState = callback.getQueryParameter("state")
            val error = callback.getQueryParameter("error")

            if (error != null) return@withContext Result.failure(
                IllegalStateException("Cloudflare authorization failed: $error")
            )
            if (code.isNullOrBlank() || expectedState.isNullOrBlank() || verifier.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Incomplete OAuth callback."))
            }
            if (returnedState != expectedState) {
                return@withContext Result.failure(SecurityException("OAuth state validation failed."))
            }

            try {
                val response = postForm(
                    CloudflareOAuthConfig.TOKEN_ENDPOINT,
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to CloudflareOAuthConfig.CLIENT_ID,
                        "redirect_uri" to CloudflareOAuthConfig.REDIRECT_URI,
                        "code" to code,
                        "code_verifier" to verifier
                    )
                )
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare token exchange failed (${response.code}).")
                    )
                }

                val json = JSONObject(response.body)
                val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(
                        IllegalStateException("Cloudflare did not return an access token.")
                    )

                secureStore.put("access_token", accessToken)
                json.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
                    secureStore.put("refresh_token", it)
                }
                json.optLong("expires_in", 0).takeIf { it > 0 }?.let {
                    secureStore.put("expires_at", (System.currentTimeMillis() + it * 1000L).toString())
                }

                val userResponse = getAuthorized(CloudflareOAuthConfig.USERINFO_ENDPOINT, accessToken)
                if (userResponse.code !in 200..299) {
                    secureStore.clear()
                    return@withContext Result.failure(
                        IllegalStateException("Cloudflare userinfo request failed (${userResponse.code}).")
                    )
                }

                val user = JSONObject(userResponse.body)
                user.optString("sub").takeIf { it.isNotBlank() }?.let { secureStore.put("subject", it) }
                user.optString("email").takeIf { it.isNotBlank() }?.let { secureStore.put("email", it) }
                user.optString("name").takeIf { it.isNotBlank() }?.let { secureStore.put("name", it) }

                prefs.edit().remove("oauth_state").remove("pkce_verifier").apply()
                Result.success(session() ?: error("Session was not created"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun disconnect() {
        secureStore.clear()
    }

    private fun postForm(urlString: String, fields: Map<String, String>): HttpResponse {
        val body = fields.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return readResponse(connection)
    }

    private fun getAuthorized(urlString: String, token: String): HttpResponse {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code, body)
    }

    private fun randomUrlSafe(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private data class HttpResponse(val code: Int, val body: String)
}
