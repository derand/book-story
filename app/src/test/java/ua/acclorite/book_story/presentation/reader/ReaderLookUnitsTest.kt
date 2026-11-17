/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.acclorite.book_story.presentation.reader.model.ReaderColorEffects
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment

/**
 * The arithmetic behind the reader's look, moved out of the screen unchanged —
 * these pin the rules that are easy to "simplify" by mistake.
 */
class ReaderLookUnitsTest {

    @Test
    fun `line height is the font size plus the setting, letter spacing is in hundredths of an em`() {
        assertEquals(20.sp, ReaderLookUnits.lineHeight(fontSize = 16, lineHeight = 4))
        assertEquals(0.05f.em, ReaderLookUnits.letterSpacing(5))
    }

    @Test
    fun `centred and end-aligned text gets no first-line indent`() {
        assertEquals(18.sp, ReaderLookUnits.paragraphIndentation(3, ReaderTextAlignment.START))
        assertEquals(18.sp, ReaderLookUnits.paragraphIndentation(3, ReaderTextAlignment.JUSTIFY))
        assertEquals(0.sp, ReaderLookUnits.paragraphIndentation(3, ReaderTextAlignment.CENTER))
        assertEquals(0.sp, ReaderLookUnits.paragraphIndentation(3, ReaderTextAlignment.END))
    }

    @Test
    fun `justified text lines up at the start`() {
        assertEquals(
            Alignment.Start,
            ReaderLookUnits.horizontalAlignment(ReaderTextAlignment.JUSTIFY)
        )
        assertEquals(
            Alignment.CenterHorizontally,
            ReaderLookUnits.horizontalAlignment(ReaderTextAlignment.CENTER)
        )
    }

    @Test
    fun `the paragraph gap is at least half the line-height setting, scaled with the font`() {
        val density = Density(density = 2f, fontScale = 1.3f)

        // 10 sp at font scale 1.3 is 13 dp, half of it 6.5 dp.
        assertEquals(6.5f.dp, ReaderLookUnits.paragraphHeight(0, lineHeight = 10, density))
        assertEquals(12.dp, ReaderLookUnits.paragraphHeight(4, lineHeight = 10, density))
    }

    @Test
    fun `highlight thickness steps up from medium`() {
        assertEquals(FontWeight.Medium, ReaderLookUnits.highlightedReadingThickness(1))
        assertEquals(FontWeight.SemiBold, ReaderLookUnits.highlightedReadingThickness(2))
        assertEquals(FontWeight.Bold, ReaderLookUnits.highlightedReadingThickness(3))
    }

    @Test
    fun `image corners scale with the clamped image width`() {
        assertEquals(0.01f, ReaderLookUnits.imagesWidth(0f))
        assertEquals(6.dp, ReaderLookUnits.imagesCornersRoundness(4, imagesWidth = 0.5f))
    }

    @Test
    fun `image colour effects`() {
        val font = Color.White
        val background = Color.Black

        assertNull(ReaderLookUnits.imagesColorEffects(ReaderColorEffects.OFF, font, background))
        assertEquals(
            ColorFilter.tint(font, BlendMode.Color),
            ReaderLookUnits.imagesColorEffects(ReaderColorEffects.FONT, font, background)
        )
        assertEquals(
            ColorFilter.tint(background, BlendMode.Color),
            ReaderLookUnits.imagesColorEffects(ReaderColorEffects.BACKGROUND, font, background)
        )
        // Equal from one call to the next, so a rebuilt look still compares equal.
        assertEquals(
            ReaderLookUnits.imagesColorEffects(ReaderColorEffects.GRAYSCALE, font, background),
            ReaderLookUnits.imagesColorEffects(ReaderColorEffects.GRAYSCALE, font, background)
        )
    }

    @Test
    fun `gesture sensitivity runs from 36 dp of pull down to 4`() {
        assertEquals(36.dp, ReaderLookUnits.horizontalGestureSensitivity(0f))
        assertEquals(4.dp, ReaderLookUnits.horizontalGestureSensitivity(1f))
    }
}
