package dk.kvitteringsarkiv.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object DropboxOAuth {
    const val REDIRECT_URI = "kvitteringsarkiv://dropbox-auth"
    private const val CLIENT_IDENTIFIER = "Kvitteringsarkiv/0.2"

    fun start(context: Context, settings: DropboxSettings): Result<Unit> = runCatching {
        val key = settings.appKey.trim()
        require(key.isNotBlank()) { "Indtast først Dropbox App Key." }

        val verifier = createVerifier()
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val state = UUID.randomUUID().toString()

        settings.pendingVerifier = verifier
        settings.pendingState = state

        val authUri = Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", key)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("token_access_type", "offline")
            .appendQueryParameter("scope", "account_info.read files.content.read files.content.write")
            .appendQueryParameter("state", state)
            .build()

        context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
    }

    fun complete(redirect: Uri, settings: DropboxSettings): Result<Unit> = runCatching {
        redirect.getQueryParameter("error")?.let { err ->
            val description = redirect.getQueryParameter("error_description") ?: err
            error("Dropbox-login blev afvist: $description")
        }

        val code = requireNotNull(redirect.getQueryParameter("code")) { "Dropbox returnerede ingen login-kode." }
        val state = redirect.getQueryParameter("state")
        require(state != null && state == settings.pendingState) { "Dropbox-login kunne ikke valideres. Prøv igen." }
        val verifier = requireNotNull(settings.pendingVerifier) { "Login-sessionen er udløbet. Prøv igen." }
        val key = settings.appKey

        val form = linkedMapOf(
            "code" to code,
            "grant_type" to "authorization_code",
            "client_id" to key,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier,
        )

        val tokenJson = postForm("https://api.dropboxapi.com/oauth2/token", form)
        settings.accessToken = tokenJson.getString("access_token")
        settings.refreshToken = tokenJson.optString("refresh_token").ifBlank { settings.refreshToken }
        val expiresIn = tokenJson.optLong("expires_in", 14400L)
        settings.expiresAt = System.currentTimeMillis() + expiresIn * 1000L
        require(!settings.refreshToken.isNullOrBlank()) { "Dropbox gav ingen refresh token. Fjern forbindelsen og prøv igen." }

        settings.pendingVerifier = null
        settings.pendingState = null

        DropboxApi.getCurrentAccount(settings).onSuccess {
            settings.accountName = it.optJSONObject("name")?.optString("display_name")
            settings.accountEmail = it.optString("email")
        }
    }

    private fun createVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun postForm(url: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("User-Agent", CLIENT_IDENTIFIER)
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Dropbox OAuth-fejl ${connection.responseCode}: $response")
            }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
