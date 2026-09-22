package dk.kvitteringsarkiv.app.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.io.File

class SafStorageProvider(private val settings: StorageSettings) : StorageProvider {
    override val id: String = ID
    override val displayName: String = "Valgt mappe"

    override fun isConfigured(context: Context): Boolean = settings.safRootUri != null

    override fun savePdf(context: Context, receipt: ReceiptDraft, sourcePdf: File): Result<StoredFile> = runCatching {
        val rootUri = requireNotNull(settings.safRootUri) { "Vælg først en mappe under Indstillinger." }
        var folder = requireNotNull(DocumentFile.fromTreeUri(context, rootUri)) { "Den valgte mappe kan ikke åbnes." }

        for (name in ReceiptPath.folders(receipt)) {
            folder = folder.findFile(name)?.takeIf { it.isDirectory } ?: requireNotNull(folder.createDirectory(name)) {
                "Kunne ikke oprette mappen $name"
            }
        }

        val desired = ReceiptPath.fileName(receipt)
        val unique = uniqueName(folder, desired)
        val target = requireNotNull(folder.createFile("application/pdf", unique)) {
            "Kunne ikke oprette PDF-filen."
        }

        context.contentResolver.openOutputStream(target.uri, "w").use { output ->
            requireNotNull(output) { "Kunne ikke skrive til destinationen." }
            sourcePdf.inputStream().use { input -> input.copyTo(output) }
        }

        StoredFile(id, (ReceiptPath.folders(receipt) + unique).joinToString(" / "))
    }

    private fun uniqueName(folder: DocumentFile, desired: String): String {
        if (folder.findFile(desired) == null) return desired
        val stem = desired.removeSuffix(".pdf")
        var n = 2
        while (folder.findFile("$stem ($n).pdf") != null) n++
        return "$stem ($n).pdf"
    }

    companion object { const val ID = "saf" }
}
