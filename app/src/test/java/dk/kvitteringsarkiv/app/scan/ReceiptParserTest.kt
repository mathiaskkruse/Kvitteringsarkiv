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

    @Test fun parsesFotexReceiptWithSpaceSeparatedDate() {
        val text = """
            Føtex
            www.fotex.dk
            FØTEX KORSØR          22 08 26
            TOTAL                 202,22
            DANKORT               202,22
            TLF. 58311000         22 08 2026
        """.trimIndent()

        val r = ReceiptParser.parse(text, LocalDate.of(2026, 9, 22))
        assertEquals("Føtex", r.supplier)
        assertEquals(LocalDate.of(2026, 8, 22), r.purchaseDate)
        assertEquals(BigDecimal("202.22"), r.total)
    }

    @Test fun fuzzyMatchesBadFotexOcr() {
        val text = """
            TOIEx
            Se 8bn ingstiderpa www.TOIEx n
            22 08 26
            TOTAL 202,22
        """.trimIndent()

        val r = ReceiptParser.parse(text, LocalDate.of(2026, 9, 22))
        assertEquals("Føtex", r.supplier)
        assertEquals(LocalDate.of(2026, 8, 22), r.purchaseDate)
    }

    @Test fun unknownSupplierDoesNotInventRandomLine() {
        val r = ReceiptParser.parse("9rs \\5V 19) 196v 9bu2\n22-09-2026\nTotal 100,00")
        assertEquals("Ukendt leverandør", r.supplier)
        assertNotNull(r.total)
    }
}
