package dk.kvitteringsarkiv.app.scan

import dk.kvitteringsarkiv.app.data.ReceiptDraft
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

object ReceiptParser {
    private data class SupplierRule(
        val canonical: String,
        val aliases: List<String>,
        val domains: List<String> = emptyList(),
    )

    private val suppliers = listOf(
        SupplierRule("Føtex", listOf("føtex", "fotex", "foetex"), listOf("fotex.dk", "foetex.dk")),
        SupplierRule("Jem & Fix", listOf("jem & fix", "jem og fix", "jemfix", "jem-fix"), listOf("jemogfix.dk", "jemfix.dk")),
        SupplierRule("Bauhaus", listOf("bauhaus"), listOf("bauhaus.dk")),
        SupplierRule("Harald Nyborg", listOf("harald nyborg"), listOf("harald-nyborg.dk", "haraldnyborg.dk")),
        SupplierRule("STARK", listOf("stark"), listOf("stark.dk")),
        SupplierRule("XL-BYG", listOf("xl-byg", "xl byg"), listOf("xl-byg.dk")),
        SupplierRule("Silvan", listOf("silvan"), listOf("silvan.dk")),
        SupplierRule("Biltema", listOf("biltema"), listOf("biltema.dk")),
        SupplierRule("thansen", listOf("thansen", "t hansen"), listOf("thansen.dk")),
        SupplierRule("Netto", listOf("netto"), listOf("netto.dk")),
        SupplierRule("Bilka", listOf("bilka"), listOf("bilka.dk")),
        SupplierRule("REMA 1000", listOf("rema 1000", "rema1000"), listOf("rema1000.dk")),
        SupplierRule("Lidl", listOf("lidl"), listOf("lidl.dk")),
        SupplierRule("Meny", listOf("meny"), listOf("meny.dk")),
        SupplierRule("SuperBrugsen", listOf("superbrugsen", "super brugsen"), listOf("superbrugsen.dk")),
        SupplierRule("365discount", listOf("365discount", "365 discount"), listOf("365discount.dk")),
        SupplierRule("Circle K", listOf("circle k", "circlek"), listOf("circlek.dk")),
        SupplierRule("Q8", listOf("q8"), listOf("q8.dk")),
        SupplierRule("IKEA", listOf("ikea"), listOf("ikea.dk")),
        SupplierRule("Elgiganten", listOf("elgiganten"), listOf("elgiganten.dk")),
        SupplierRule("POWER", listOf("power"), listOf("power.dk")),
    )

    private val dateRegexes = listOf(
        Regex("""\b(\d{1,2})\s*[.\-/ ]\s*(\d{1,2})\s*[.\-/ ]\s*(20\d{2})\b"""),
        Regex("""\b(20\d{2})\s*[.\-/ ]\s*(\d{1,2})\s*[.\-/ ]\s*(\d{1,2})\b"""),
        Regex("""\b(\d{1,2})\s*[.\-/ ]\s*(\d{1,2})\s*[.\-/ ]\s*(\d{2})\b"""),
    )

    private val amountRegex = Regex("(?<!\\d)(\\d{1,3}(?:[ .]\\d{3})*|\\d+)[,.](\\d{2})(?!\\d)")
    private val totalWords = listOf("total", "i alt", "ialt", "at betale", "beløb", "beloeb", "sum")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptDraft =
        parseCandidates(listOf(text), today)

    fun parseCandidates(texts: List<String>, today: LocalDate = LocalDate.now()): ReceiptDraft {
        val clean = texts.filter { it.isNotBlank() }
        val combined = clean.joinToString("\n")
        val bestText = clean.maxByOrNull(::textQualityScore).orEmpty()

        val supplier = detectSupplier(combined)
        val detectedDate = detectDate(combined)
        val date = detectedDate ?: today
        val total = detectTotal(combined)

        var confidence = 0.10f
        if (supplier != "Ukendt leverandør") confidence += 0.40f
        if (detectedDate != null) confidence += 0.25f
        if (total != null) confidence += 0.25f

        return ReceiptDraft(
            supplier = supplier,
            purchaseDate = date,
            total = total,
            ocrText = bestText,
            confidence = confidence.coerceAtMost(1f),
            purchaseDateDetected = detectedDate != null,
        )
    }

    private fun textQualityScore(text: String): Int {
        var score = text.count { it.isLetterOrDigit() }
        val normalized = normalize(text)
        if ("total" in normalized) score += 120
        if (detectDate(text) != null) score += 140
        if (detectSupplier(text) != "Ukendt leverandør") score += 160
        if (amountRegex.containsMatchIn(text)) score += 60
        return score
    }

    private fun detectSupplier(original: String): String {
        val normalizedWhole = normalize(original)

        suppliers.firstOrNull { rule ->
            rule.domains.any { normalize(it) in normalizedWhole }
        }?.let { return it.canonical }

        suppliers.firstOrNull { rule ->
            rule.aliases.any { normalize(it) in normalizedWhole }
        }?.let { return it.canonical }

        val candidateTokens = original.lineSequence()
            .take(24)
            .flatMap { line ->
                normalize(line)
                    .split(Regex("[^a-z0-9&]+"))
                    .asSequence()
            }
            .map { it.trim() }
            .filter { it.length >= 4 }
            .toList()

        var bestRule: SupplierRule? = null
        var bestDistance = Int.MAX_VALUE

        for (rule in suppliers) {
            for (alias in rule.aliases) {
                val aliasToken = normalize(alias).replace(Regex("[^a-z0-9]"), "")
                if (aliasToken.length < 5) continue

                for (token in candidateTokens) {
                    val compactToken = token.replace(Regex("[^a-z0-9]"), "")
                    if (compactToken.length !in (aliasToken.length - 2)..(aliasToken.length + 2)) continue

                    val distance = levenshtein(compactToken, aliasToken)
                    val allowed = when {
                        aliasToken.length >= 9 -> 2
                        aliasToken.length >= 5 -> 2
                        else -> 1
                    }

                    if (distance <= allowed && distance < bestDistance) {
                        bestDistance = distance
                        bestRule = rule
                    }
                }
            }
        }

        return bestRule?.canonical ?: "Ukendt leverandør"
    }

    private fun detectDate(text: String): LocalDate? {
        for ((index, regex) in dateRegexes.withIndex()) {
            for (match in regex.findAll(text)) {
                try {
                    val g = match.groupValues
                    val date = when (index) {
                        0 -> LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                        1 -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                        else -> LocalDate.of(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt())
                    }
                    if (date.year in 2000..2100) return date
                } catch (_: Exception) {
                }
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
        try {
            BigDecimal("$integer.$decimals")
        } catch (_: NumberFormatException) {
            null
        }
    }.toList()

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('æ', 'a')
        .replace('ø', 'o')
        .replace('å', 'a')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val insert = current[j] + 1
                val delete = previous[j + 1] + 1
                val replace = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(insert, delete, replace)
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }
}
