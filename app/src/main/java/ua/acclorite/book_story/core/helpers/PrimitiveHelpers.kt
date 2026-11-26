/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import java.math.RoundingMode
import java.text.NumberFormat

/**
 * This fraction as a percentage for the app language (the default locale):
 * its digits and decimal separator, rounded half up to at most [digits]
 * fraction digits, without trailing zeros, and never with a minus sign.
 */
fun Float.calculateProgress(digits: Int): String {
    val percent = (this * 100).toDouble()
    return NumberFormat.getInstance().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = digits
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = false
    }.format(if (percent > 0.0) percent else 0.0)
}

fun Float?.coerceAndPreventNaN(): Float {
    if (this == null) return 0f
    if (isNaN()) return 0f
    return this.coerceIn(0f, 1f)
}

fun String.containsVisibleText(): Boolean {
    return any { it.isVisibleCharacter() }
}

fun Char.isVisibleCharacter(): Boolean {
    return when (this.category) {
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.DECIMAL_DIGIT_NUMBER,
        CharCategory.LETTER_NUMBER,
        CharCategory.OTHER_NUMBER,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.OTHER_SYMBOL,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION -> true

        else -> false
    }
}