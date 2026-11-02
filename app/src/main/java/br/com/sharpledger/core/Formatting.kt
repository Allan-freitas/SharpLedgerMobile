package br.com.sharpledger.core

import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Date
import java.util.Locale

object MoneyFormat {
    private val brl: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply {
        currency = Currency.getInstance("BRL")
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun format(value: BigDecimal?): String = brl.format(value ?: BigDecimal.ZERO)

    fun formatPlain(value: BigDecimal?): String {
        val formatter = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
            isGroupingUsed = false
        }
        return formatter.format(value ?: BigDecimal.ZERO)
    }

    fun formatQuantity(value: BigDecimal?): String {
        val formatter = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            maximumFractionDigits = 8
            minimumFractionDigits = 0
        }
        return formatter.format(value ?: BigDecimal.ZERO)
    }

    fun parse(text: String?): BigDecimal? {
        if (text.isNullOrBlank()) return null
        var cleaned = text.trim()
            .replace("R$", "")
            .replace(" ", "")
            .replace("\u00A0", "")

        cleaned = when {
            cleaned.contains(",") && cleaned.contains(".") -> cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(",") -> cleaned.replace(",", ".")
            else -> cleaned
        }

        return runCatching { BigDecimal(cleaned) }.getOrNull()
    }
}

object Dates {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val brFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val brDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    fun today(): String = LocalDate.now().toString()

    fun parseIso(value: String?): LocalDate? =
        runCatching { LocalDate.parse(value ?: "", isoFormatter) }.getOrNull()

    fun formatIso(value: String?): String =
        parseIso(value)?.format(brFormatter) ?: (value ?: "")

    fun formatMillis(millis: Long): String = brDateFormatter.format(Date(millis))

    fun monthLabel(month: YearMonth): String {
        val locale = Locale.getDefault()
        val monthName = month.month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        return "$monthName ${month.year}"
    }

    fun fromIsoDate(value: String?): Long? {
        val date = parseIso(value) ?: return null
        return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

object AppVersion {
    const val VERSION_NAME = "1.0"
}
