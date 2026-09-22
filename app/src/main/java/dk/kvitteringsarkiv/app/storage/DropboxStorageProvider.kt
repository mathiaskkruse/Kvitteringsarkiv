package dk.kvitteringsarkiv.app.storage

import android.content.Context
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.io.File

class DropboxStorageProvider(
    private val settings: DropboxSettings,
) : StorageProvider {
    override val id: String = ID
    override val displayName: String = "Dropbox"

    override fun isConfigured(context: Context): Boolean =
        settings.appKey.isNotBlank() && settings.isConnected

    override fun savePdf(
        context: Context,
        receipt: ReceiptDraft,
        sourcePdf: File,
    ): Result<StoredFile> =
        DropboxApi.uploadReceipt(settings, receipt, sourcePdf)
            .map { remotePath -> StoredFile(ID, remotePath) }

    companion object {
        const val ID = "dropbox"
    }
}
