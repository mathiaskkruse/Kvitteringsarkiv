package dk.kvitteringsarkiv.app.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrService {
    fun recognize(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    onSuccess(result.text)
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    onFailure(error)
                }
        } catch (error: Throwable) {
            onFailure(error)
        }
    }
}
