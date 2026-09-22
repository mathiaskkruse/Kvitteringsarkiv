package dk.kvitteringsarkiv.app.storage

import android.content.Context
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.io.File

/**
 * Provider boundaries for the first cloud integrations.
 * OAuth/API credentials deliberately do not live in source control.
 * See README.md for the app-registration work needed before enabling each provider.
 */
abstract class CloudStorageProvider(
    override val id: String,
    override val displayName: String,
) : StorageProvider {
    override fun isConfigured(context: Context): Boolean = false
    override fun savePdf(context: Context, receipt: ReceiptDraft, sourcePdf: File): Result<StoredFile> =
        Result.failure(IllegalStateException("${displayName} er klar i arkitekturen, men OAuth er ikke konfigureret i denne build."))
}

class GoogleDriveStorageProvider : CloudStorageProvider("google_drive", "Google Drive")
class OneDriveStorageProvider : CloudStorageProvider("onedrive", "OneDrive")
