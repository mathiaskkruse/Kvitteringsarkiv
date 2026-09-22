package dk.kvitteringsarkiv.app.storage

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object DropboxApi {
    private const val API = "https://api.dropboxapi.com/2"
    private const val CONTENT = "https://content.dropboxapi.com/2"
    private const val TOKEN = "https://api.dropboxapi.com/oauth2/token"

    fun getCurrentAccount(settings: DropboxSettings): Result<JSONObject> = runCatching {
        val token = validAccessToken(settings)
        postJson("$API/users/get_current_account", token, JSONObject())
    }

    fun uploadReceipt(
        settings: DropboxSettings,
        receipt: dk.kvitteringsarkiv.app.data.ReceiptDraft,
        sourcePdf: File,
    ): Result<String> = runCatching {
        val token = validAccessToken(settings)
        val root = sanitizeSegment(settings.rootFolder)
        val folders = listOf(root) + ReceiptPath.folders(receipt).map(::sanitizeSegment)
        var current = ""
        folders.forEach { segment ->
            current += "/$segment"
            ensureFolder(token, current)
        }

        val target = "$current/${ReceiptPath.fileName(receipt)}"
        upload(token, target, sourcePdf)
    }

    fun downloadToCache(
        context: Context,
        settings: DropboxSettings,
        remotePath: String,
    ): Result<File> = runCatching {
        val token = validAccessToken(settings)
        val fileName = remotePath.substringAfterLast('/').ifBlank { "kvittering.pdf" }
        val dir = File(context.cacheDir, "dropbox").apply { mkdirs() }
        val target = File(dir, fileName)

        val conn = URL("$CONTENT/files/download").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Dropbox-API-Arg", asciiJson(JSONObject().put("path", remotePath)))
            if (conn.responseCode !in 200..299) {
                val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Dropbox download-fejl ${conn.responseCode}: $msg")
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } finally {
            conn.disconnect()
        }
    }

    private fun validAccessToken(settings: DropboxSettings): String {
        val current = settings.accessToken
        if (!current.isNullOrBlank() && settings.expiresAt > System.currentTimeMillis() + 120_000L) {
            return current
        }

        val refresh = requireNotNull(settings.refreshToken) { "Dropbox-forbindelsen er udløbet. Forbind kontoen igen." }
        val key = settings.appKey
        val body = linkedMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refresh,
            "client_id" to key,
        ).entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

        val conn = URL(TOKEN).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Kunne ikke forny Dropbox-login: $response")
            }
            val json = JSONObject(response)
            val token = json.getString("access_token")
            settings.accessToken = token
            settings.expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 14400L) * 1000L
            return token
        } finally {
            conn.disconnect()
        }
    }

    private fun ensureFolder(token: String, path: String) {
        val conn = URL("$API/files/create_folder_v2").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = JSONObject().put("path", path).put("autorename", false).toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299 && code != 409) {
                val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Kunne ikke oprette Dropbox-mappe: $msg")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun upload(token: String, remotePath: String, sourcePdf: File): String {
        val conn = URL("$CONTENT/files/upload").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val args = JSONObject()
                .put("path", remotePath)
                .put("mode", "add")
                .put("autorename", true)
                .put("mute", false)
            conn.setRequestProperty("Dropbox-API-Arg", asciiJson(args))
            conn.doOutput = true
            sourcePdf.inputStream().use { input ->
                conn.outputStream.use { output -> input.copyTo(output) }
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Dropbox upload-fejl ${conn.responseCode}: $response")
            }
            JSONObject(response).optString("path_display", remotePath)
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, token: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Dropbox API-fejl ${conn.responseCode}: $response")
            }
            return JSONObject(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun sanitizeSegment(value: String): String = value
        .replace(Regex("[/\\\\]"), "-")
        .trim()
        .ifBlank { "Kvitteringer" }

    private fun asciiJson(json: JSONObject): String {
        val raw = json.toString()
        val out = StringBuilder(raw.length)
        raw.forEach { c ->
            if (c.code in 32..126) out.append(c)
            else out.append(String.format("\\u%04x", c.code))
        }
        return out.toString()
    }
}
