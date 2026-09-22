package dk.kvitteringsarkiv.app.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptParserTest {
    @Test fun parsesJemFixReceipt() {
        val text = """
            JEM & FIX A/S
            Slagelse
            Dato 18.09.2026
            Skruer 99,95
            TOTAL DKK 487,50
        """.trimIndent()
        val r = ReceiptParser.parse(text, LocalDate.of(2026, 9, 22))
        assertEquals("Jem & Fix", r.supplier)
        assertEquals(LocalDate.of(2026, 9, 18), r.purchaseDate)
        assertEquals(BigDecimal("487.50"), r.total)
    }

    @Test fun parsesThousandsAndDanishTotalWord() {
        val text = """
            BAUHAUS
            21/09/2026
            I ALT 1.249,00
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        assertEquals("Bauhaus", r.supplier)
        assertEquals(BigDecimal("1249.00"), r.total)
    }

    @Test fun unknownSupplierFallsBackToFirstTextLine() {
        val r = ReceiptParser.parse("Lokalt Byggemarked ApS\n22-09-2026\nTotal 100,00")
        assertEquals("Lokalt Byggemarked ApS", r.supplier)
        assertNotNull(r.total)
    }
}
