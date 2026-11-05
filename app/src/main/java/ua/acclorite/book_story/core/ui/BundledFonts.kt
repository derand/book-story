/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.ui

import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import ua.acclorite.book_story.R

/**
 * The font for code. It ships with the app because [FontFamily.Monospace] is
 * only a name: a system font theme can point every named family at its own
 * font, proportional or not, and code then loses its columns (seen on a
 * Motorola phone, Android 16).
 */
val MonospaceFontFamily: FontFamily = provideVariableFontFamily(
    normalResId = R.font.robotomono_variable,
    italicResId = R.font.robotomono_variable_italic
)

@OptIn(ExperimentalTextApi::class)
fun provideVariableFontFamily(
    @FontRes normalResId: Int,
    @FontRes italicResId: Int
): FontFamily {
    fun provideVariableFont(weight: FontWeight): Array<Font> {
        return arrayOf(
            Font(
                resId = normalResId,
                weight = weight,
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(
                    weight = weight,
                    style = FontStyle.Normal
                )
            ),
            Font(
                resId = italicResId,
                weight = weight,
                style = FontStyle.Italic,
                variationSettings = FontVariation.Settings(
                    weight = weight,
                    style = FontStyle.Italic
                )
            )
        )
    }

    return FontFamily(
        *provideVariableFont(weight = FontWeight.W100),
        *provideVariableFont(weight = FontWeight.W200),
        *provideVariableFont(weight = FontWeight.W300),
        *provideVariableFont(weight = FontWeight.W400),
        *provideVariableFont(weight = FontWeight.W500),
        *provideVariableFont(weight = FontWeight.W600),
        *provideVariableFont(weight = FontWeight.W700),
        *provideVariableFont(weight = FontWeight.W800),
        *provideVariableFont(weight = FontWeight.W900)
    )
}