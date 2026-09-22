package dk.kvitteringsarkiv.app.storage

import android.content.Context

class DropboxSettings(context: Context) {
    private val prefs = context.getSharedPreferences("dropbox_settings", Context.MODE_PRIVATE)

    var appKey: String
        get() = prefs.getString("app_key", "") ?: ""
        set(value) = prefs.edit().putString("app_key", value.trim()).apply()

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var expiresAt: Long
        get() = prefs.getLong("expires_at", 0L)
        set(value) = prefs.edit().putLong("expires_at", value).apply()

    var accountName: String?
        get() = prefs.getString("account_name", null)
        set(value) = prefs.edit().putString("account_name", value).apply()

    var accountEmail: String?
        get() = prefs.getString("account_email", null)
        set(value) = prefs.edit().putString("account_email", value).apply()

    var rootFolder: String
        get() = prefs.getString("root_folder", "Kvitteringer") ?: "Kvitteringer"
        set(value) = prefs.edit().putString("root_folder", value.trim().trim('/').ifBlank { "Kvitteringer" }).apply()

    var pendingVerifier: String?
        get() = prefs.getString("pending_verifier", null)
        set(value) = prefs.edit().putString("pending_verifier", value).apply()

    var pendingState: String?
        get() = prefs.getString("pending_state", null)
        set(value) = prefs.edit().putString("pending_state", value).apply()

    val isConnected: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun clearAuth() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .remove("account_name")
            .remove("account_email")
            .remove("pending_verifier")
            .remove("pending_state")
            .apply()
    }
}
