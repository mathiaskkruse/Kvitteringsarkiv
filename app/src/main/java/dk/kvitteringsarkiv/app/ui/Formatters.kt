package dk.kvitteringsarkiv.app.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dk = Locale.forLanguageTag("da-DK")
private val dateFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", dk)
private val amountFormatter = NumberFormat.getCurrencyInstance(dk)

fun LocalDate.dkDate(): String = format(dateFormatter)
fun BigDecimal.dkAmount(): String = amountFormatter.format(this)
