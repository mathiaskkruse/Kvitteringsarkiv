package dk.kvitteringsarkiv.app.storage

import android.content.Context
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.io.File

data class StoredFile(val providerId: String, val path: String)

interface StorageProvider {
    val id: String
    val displayName: String
    fun isConfigured(context: Context): Boolean
    fun savePdf(context: Context, receipt: ReceiptDraft, sourcePdf: File): Result<StoredFile>
}
