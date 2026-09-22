package dk.kvitteringsarkiv.app.data

import java.math.BigDecimal
import java.time.LocalDate

data class ReceiptDraft(
    val supplier: String,
    val purchaseDate: LocalDate,
    val total: BigDecimal?,
    val vat: BigDecimal? = null,
    val receiptNumber: String? = null,
    val ocrText: String,
    val confidence: Float = 0f,
)

data class ReceiptRecord(
    val id: Long,
    val supplier: String,
    val purchaseDate: LocalDate,
    val total: BigDecimal?,
    val storageProvider: String,
    val storagePath: String,
    val ocrText: String,
    val createdAt: Long,
)
