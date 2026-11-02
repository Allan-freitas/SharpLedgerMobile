package br.com.sharpledger

import br.com.sharpledger.core.AppPrefs
import br.com.sharpledger.core.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatTest {
    @Test
    fun parsesBrazilianDecimal() {
        assertEquals(BigDecimal("1234.56"), MoneyFormat.parse("1.234,56"))
    }

    @Test
    fun parsesCommaDecimal() {
        assertEquals(BigDecimal("50.5"), MoneyFormat.parse("50,5"))
    }

    @Test
    fun parsesPlainDecimal() {
        assertEquals(BigDecimal("10.25"), MoneyFormat.parse("10.25"))
    }

    @Test
    fun rejectsInvalidValue() {
        assertNull(MoneyFormat.parse("abc"))
    }

    @Test
    fun normalizesBaseUrl() {
        assertEquals(
            "http://10.0.2.2:5080/",
            AppPrefs.normalizeBaseUrl("10.0.2.2:5080"),
        )
        assertEquals(
            "https://api.example.com/",
            AppPrefs.normalizeBaseUrl("https://api.example.com"),
        )
    }
}
