/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.reader.reading_mode.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The overlap slider steps in halves of a line but speaks in lines. */
class PageTurnOverlapOptionTest {

    @Test
    fun aHalfLineIsWrittenAsAHalf() {
        assertEquals("0.5", overlapLabel(1))
        assertEquals("1.5", overlapLabel(3))
        assertEquals("3.5", overlapLabel(7))
    }

    @Test
    fun aWholeLineCarriesNoDecimal() {
        // "2" and not "2.0": the trailing zero reads as precision that is not
        // being offered.
        assertEquals("1", overlapLabel(2))
        assertEquals("2", overlapLabel(4))
        assertEquals("4", overlapLabel(8))
    }
}
