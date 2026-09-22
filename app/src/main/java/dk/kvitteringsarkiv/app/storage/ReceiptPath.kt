package dk.kvitteringsarkiv.app.storage

import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.math.RoundingMode
import java.time.Month

object ReceiptPath {
    private val danishMonths = mapOf(
        Month.JANUARY to "Januar", Month.FEBRUARY to "Februar", Month.MARCH to "Marts",
        Month.APRIL to "April", Month.MAY to "Maj", Month.JUNE to "Juni",
        Month.JULY to "Juli", Month.AUGUST to "August", Month.SEPTEMBER to "September",
        Month.OCTOBER to "Oktober", Month.NOVEMBER to "November", Month.DECEMBER to "December",
    )

    fun folders(receipt: ReceiptDraft): List<String> = listOf(
        sanitize(receipt.supplier),
        receipt.purchaseDate.year.toString(),
        danishMonths.getValue(receipt.purchaseDate.month),
    )

    fun fileName(receipt: ReceiptDraft): String {
        val amount = receipt.total?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',')
        return if (amount != null) "${receipt.purchaseDate} - $amount kr.pdf" else "${receipt.purchaseDate} - kvittering.pdf"
    }

    fun displayPath(receipt: ReceiptDraft): String = (folders(receipt) + fileName(receipt)).joinToString(" / ")

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Ukendt leverandør" }
}
