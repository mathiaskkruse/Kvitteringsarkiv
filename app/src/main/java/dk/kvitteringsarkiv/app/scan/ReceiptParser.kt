package dk.kvitteringsarkiv.app.scan

import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

object ReceiptParser {
    private data class SupplierRule(val canonical: String, val needles: List<String>)

    private val suppliers = listOf(
        SupplierRule("Jem & Fix", listOf("jem & fix", "jem og fix", "jemfix", "jem-fix")),
        SupplierRule("Bauhaus", listOf("bauhaus")),
        SupplierRule("Harald Nyborg", listOf("harald nyborg")),
        SupplierRule("STARK", listOf("stark")),
        SupplierRule("XL-BYG", listOf("xl-byg", "xl byg")),
        SupplierRule("Silvan", listOf("silvan")),
        SupplierRule("Biltema", listOf("biltema")),
        SupplierRule("thansen", listOf("thansen", "t hansen")),
    )

    private val datePatterns = listOf(
        Regex("\\b(\\d{2})[.\\-/](\\d{2})[.\\-/](20\\d{2})\\b"),
        Regex("\\b(20\\d{2})[.\\-/](\\d{2})[.\\-/](\\d{2})\\b"),
        Regex("\\b(\\d{2})[.\\-/](\\d{2})[.\\-/](\\d{2})\\b"),
    )

    private val amountRegex = Regex("(?<!\\d)(\\d{1,3}(?:[ .]\\d{3})*|\\d+)[,.](\\d{2})(?!\\d)")
    private val totalWords = listOf("total", "i alt", "ialt", "at betale", "beløb", "beloeb", "sum")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptDraft {
        val normalized = normalize(text)
        val supplier = detectSupplier(normalized, text)
        val date = detectDate(text) ?: today
        val total = detectTotal(text)

        var confidence = 0.15f
        if (suppliers.any { it.canonical == supplier }) confidence += 0.35f
        if (detectDate(text) != null) confidence += 0.25f
        if (total != null) confidence += 0.25f

        return ReceiptDraft(
            supplier = supplier,
            purchaseDate = date,
            total = total,
            ocrText = text,
            confidence = confidence.coerceAtMost(1f),
        )
    }

    private fun detectSupplier(normalized: String, original: String): String {
        suppliers.firstOrNull { rule -> rule.needles.any { normalize(it) in normalized } }?.let { return it.canonical }

        return original.lineSequence()
            .map { it.trim() }
            .firstOrNull { line -> line.length in 2..50 && line.any(Char::isLetter) }
            ?.replace(Regex("\\s+"), " ")
            ?: "Ukendt leverandør"
    }

    private fun detectDate(text: String): LocalDate? {
        for ((index, regex) in datePatterns.withIndex()) {
            for (match in regex.findAll(text)) {
                try {
                    val groups = match.groupValues
                    val date = when (index) {
                        0 -> LocalDate.of(groups[3].toInt(), groups[2].toInt(), groups[1].toInt())
                        1 -> LocalDate.of(groups[1].toInt(), groups[2].toInt(), groups[3].toInt())
                        else -> {
                            val yy = groups[3].toInt()
                            LocalDate.of(2000 + yy, groups[2].toInt(), groups[1].toInt())
                        }
                    }
                    if (date.year in 2000..2100) return date
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun detectTotal(text: String): BigDecimal? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val strongCandidates = lines.filter { line ->
            val lower = normalize(line)
            totalWords.any { it in lower }
        }.flatMap(::amountsFromLine)

        if (strongCandidates.isNotEmpty()) return strongCandidates.maxOrNull()
        return lines.flatMap(::amountsFromLine).maxOrNull()
    }

    private fun amountsFromLine(line: String): List<BigDecimal> = amountRegex.findAll(line).mapNotNull { match ->
        val integer = match.groupValues[1].replace(" ", "").replace(".", "")
        val decimals = match.groupValues[2]
        try { BigDecimal("$integer.$decimals") } catch (_: NumberFormatException) { null }
    }.toList()

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('æ', 'a')
        .replace('ø', 'o')
        .replace('å', 'a')
        .replace(Regex("\\s+"), " ")
}
