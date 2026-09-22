package dk.kvitteringsarkiv.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrService {
    fun recognize(
        context: Context,
        imageUri: Uri,
        onSuccess: (List<String>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val original = InputImage.fromFilePath(context, imageUri)
            recognizer.process(original)
                .addOnSuccessListener { first ->
                    val texts = mutableListOf(first.text)
                    Thread {
                        val variants = runCatching { createEnhancedVariants(context, imageUri) }.getOrDefault(emptyList())
                        if (variants.isEmpty()) {
                            recognizer.close()
                            onSuccess(texts.filter { it.isNotBlank() })
                        } else {
                            recognizeVariants(recognizer, variants, 0, texts) { finalTexts ->
                                recognizer.close()
                                onSuccess(finalTexts.filter { it.isNotBlank() }.distinct())
                            }
                        }
                    }.start()
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    onFailure(error)
                }
        } catch (error: Throwable) {
            recognizer.close()
            onFailure(error)
        }
    }

    private fun recognizeVariants(
        recognizer: TextRecognizer,
        variants: List<Bitmap>,
        index: Int,
        texts: MutableList<String>,
        onComplete: (List<String>) -> Unit,
    ) {
        if (index >= variants.size) {
            variants.forEach { if (!it.isRecycled) it.recycle() }
            onComplete(texts)
            return
        }

        val bitmap = variants[index]
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                texts += result.text
                recognizeVariants(recognizer, variants, index + 1, texts, onComplete)
            }
            .addOnFailureListener {
                recognizeVariants(recognizer, variants, index + 1, texts, onComplete)
            }
    }

    private fun createEnhancedVariants(context: Context, uri: Uri): List<Bitmap> {
        val source = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            requireNotNull(BitmapFactory.decodeStream(input))
        }

        val maxDimension = 4200
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height))
        val working = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== source) source.recycle() }
        } else {
            source
        }

        val enhanced = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val contrast = 1.7f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isFilterBitmap = true
        }
        canvas.drawBitmap(working, 0f, 0f, paint)
        if (working !== enhanced && !working.isRecycled) working.recycle()

        val topHeight = (enhanced.height * 0.55f).toInt().coerceAtLeast(1)
        val bottomStart = (enhanced.height * 0.45f).toInt().coerceIn(0, enhanced.height - 1)
        val bottomHeight = enhanced.height - bottomStart

        val top = Bitmap.createBitmap(enhanced, 0, 0, enhanced.width, topHeight)
        val bottom = Bitmap.createBitmap(enhanced, 0, bottomStart, enhanced.width, bottomHeight)

        return listOf(enhanced, top, bottom)
    }
}
