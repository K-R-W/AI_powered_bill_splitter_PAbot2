package com.example.pa_bot2.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A supported currency. [code] is the ISO 4217 code stored on each bill; [symbol]
 * is what we render next to amounts.
 */
data class Currency(val code: String, val symbol: String, val displayName: String)

/** Currencies offered in Settings. Order = display order. */
val SupportedCurrencies = listOf(
    Currency("INR", "₹", "Indian Rupee"),
    Currency("USD", "$", "US Dollar"),
    Currency("EUR", "€", "Euro"),
    Currency("GBP", "£", "British Pound"),
    Currency("JPY", "¥", "Japanese Yen"),
    Currency("AUD", "A$", "Australian Dollar"),
    Currency("CAD", "C$", "Canadian Dollar"),
    Currency("SGD", "S$", "Singapore Dollar"),
    Currency("AED", "د.إ", "UAE Dirham"),
    Currency("CHF", "CHF", "Swiss Franc"),
    Currency("CNY", "¥", "Chinese Yuan"),
    Currency("ZAR", "R", "South African Rand")
)

const val DEFAULT_CURRENCY_CODE = "INR"

private val currencyByCode = SupportedCurrencies.associateBy { it.code }

fun currencySymbol(code: String): String = currencyByCode[code]?.symbol ?: code

private val amountFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

/** Formats an amount with its currency symbol, e.g. "₹1,234.56" or "$1,234.56". */
fun formatMoney(amount: Double, currencyCode: String): String =
    "${currencySymbol(currencyCode)}${amountFormat.format(amount)}"
