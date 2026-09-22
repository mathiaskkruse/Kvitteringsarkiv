package dk.kvitteringsarkiv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dk.kvitteringsarkiv.app.data.ReceiptDatabase
import dk.kvitteringsarkiv.app.data.ReceiptDraft
import dk.kvitteringsarkiv.app.scan.OcrService
import dk.kvitteringsarkiv.app.scan.ReceiptParser
import dk.kvitteringsarkiv.app.storage.SafStorageProvider
import dk.kvitteringsarkiv.app.storage.StorageSettings
import dk.kvitteringsarkiv.app.ui.*
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, REVIEW, SETTINGS }
    private data class Pending(val draft: ReceiptDraft, val pdf: File)

    private val db by lazy { ReceiptDatabase(this) }
    private val storageSettings by lazy { StorageSettings(this) }
    private var screen by mutableStateOf(Screen.HOME)
    private var pending by mutableStateOf<Pending?>(null)
    private var query by mutableStateOf("")
    private var receipts by mutableStateOf(emptyList<dk.kvitteringsarkiv.app.data.ReceiptRecord>())
    private var errorMessage by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)

    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data) ?: return@registerForActivityResult
        val imageUri = result.pages?.firstOrNull()?.imageUri
        val pdfUri = result.pdf?.uri
        if (imageUri == null || pdfUri == null) {
            errorMessage = "Scanningen gav ikke både billede og PDF. Prøv igen."
            return@registerForActivityResult
        }

        val cachedPdf = copyPdfToCache(pdfUri)
        if (cachedPdf == null) {
            errorMessage = "Kunne ikke klargøre PDF-filen."
            return@registerForActivityResult
        }

        OcrService.recognize(this, imageUri,
            onSuccess = { text ->
                pending = Pending(ReceiptParser.parse(text), cachedPdf)
                screen = Screen.REVIEW
            },
            onFailure = { error ->
                cachedPdf.delete()
                errorMessage = "OCR fejlede: ${error.message ?: "ukendt fejl"}"
            }
        )
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            storageSettings.safRootUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshReceipts()
        setContent {
            KvitteringsTheme {
                when (screen) {
                    Screen.HOME -> HomeScreen(receipts, query, ::onQueryChange, ::startScanner) { screen = Screen.SETTINGS }
                    Screen.REVIEW -> pending?.let { p -> ReviewScreen(p.draft, ::cancelReview, ::saveReceipt, saving) }
                    Screen.SETTINGS -> SettingsScreen(storageSettings.safRootUri?.toString(), { folderPicker.launch(storageSettings.safRootUri) }) { screen = Screen.HOME }
                }
                errorMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
                        title = { Text("Der opstod en fejl") },
                        text = { Text(message) },
                    )
                }
            }
        }
    }

    private fun startScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender -> scannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { error -> errorMessage = "Scanner kunne ikke startes: ${error.message ?: "ukendt fejl"}" }
    }

    private fun saveReceipt(edited: ReceiptDraft) {
        val p = pending ?: return
        val provider = SafStorageProvider(storageSettings)
        if (!provider.isConfigured(this)) {
            errorMessage = "Vælg først en rodmappe under Indstillinger."
            screen = Screen.SETTINGS
            return
        }

        saving = true
        Thread {
            val result = provider.savePdf(this, edited, p.pdf)
            runOnUiThread {
                saving = false
                result.onSuccess { stored ->
                    db.insert(edited, stored.providerId, stored.path)
                    p.pdf.delete()
                    pending = null
                    query = ""
                    refreshReceipts()
                    screen = Screen.HOME
                }.onFailure { error -> errorMessage = error.message ?: "Kunne ikke gemme kvitteringen." }
            }
        }.start()
    }

    private fun cancelReview() {
        pending?.pdf?.delete()
        pending = null
        screen = Screen.HOME
    }

    private fun onQueryChange(value: String) {
        query = value
        refreshReceipts()
    }

    private fun refreshReceipts() {
        receipts = db.search(query)
    }

    private fun copyPdfToCache(uri: Uri): File? = runCatching {
        val dir = File(cacheDir, "pending").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.pdf")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }.getOrNull()
}
