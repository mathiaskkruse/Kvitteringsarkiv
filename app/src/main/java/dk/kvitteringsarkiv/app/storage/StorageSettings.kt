package dk.kvitteringsarkiv.app.storage

import android.content.Context
import android.net.Uri

class StorageSettings(context: Context) {
    private val prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE)

    var activeProvider: String
        get() = prefs.getString("active_provider", SafStorageProvider.ID) ?: SafStorageProvider.ID
        set(value) = prefs.edit().putString("active_provider", value).apply()

    var safRootUri: Uri?
        get() = prefs.getString("saf_root_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit().putString("saf_root_uri", value?.toString()).apply()
}
