/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** A slider steps in fractions of a line; the reader is told lines. */
class LineLabelTest {

    @Test
    fun aHalfLineIsWrittenAsAHalf() {
        assertEquals("0.5", lineLabel(steps = 1, stepsPerLine = 2))
        assertEquals("1.5", lineLabel(steps = 3, stepsPerLine = 2))
        assertEquals("3.5", lineLabel(steps = 7, stepsPerLine = 2))
    }

    @Test
    fun aWholeLineCarriesNoDecimal() {
        // "2" and not "2.0": the trailing zero reads as precision that is not
        // being offered.
        assertEquals("1", lineLabel(steps = 2, stepsPerLine = 2))
        assertEquals("2", lineLabel(steps = 4, stepsPerLine = 2))
        assertEquals("4", lineLabel(steps = 8, stepsPerLine = 2))
        assertEquals("0", lineLabel(steps = 0, stepsPerLine = 4))
        assertEquals("4", lineLabel(steps = 16, stepsPerLine = 4))
    }

    /** A quarter grid has to say the quarters and still say a half as a half. */
    @Test
    fun aQuarterOfALineKeepsBothOfItsDigits() {
        assertEquals("0.25", lineLabel(steps = 1, stepsPerLine = 4))
        assertEquals("0.5", lineLabel(steps = 2, stepsPerLine = 4))
        assertEquals("0.75", lineLabel(steps = 3, stepsPerLine = 4))
        assertEquals("1.75", lineLabel(steps = 7, stepsPerLine = 4))
        assertEquals("2.25", lineLabel(steps = 9, stepsPerLine = 4))
    }
}
