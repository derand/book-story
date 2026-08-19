/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** How far one page turn goes — the part of it that is arithmetic. */
class ReaderPagerTest {

    private val viewport = 2000f
    private val overlap = 60f * PAGE_TURN_OVERLAP_LINES

    @Test
    fun aFullPageStillLeavesTheCutLineOnScreen() {
        // Asking for the whole screen must not cost the line the bottom edge cut.
        val distance = pageTurnDistance(viewport, fraction = 1f, overlap = overlap)

        assertEquals(viewport - overlap, distance, TOLERANCE)
    }

    @Test
    fun aSmallerStepIsTakenAtItsWord() {
        assertEquals(1400f, pageTurnDistance(viewport, fraction = 0.7f, overlap = overlap), TOLERANCE)
    }

    @Test
    fun theOverlapOnlyEverCapsTheStep() {
        // The cap sits at 1910; 0.9 of the screen is below it and is left alone.
        assertEquals(1800f, pageTurnDistance(viewport, fraction = 0.9f, overlap = overlap), TOLERANCE)
        assertEquals(1910f, pageTurnDistance(viewport, fraction = 0.99f, overlap = 90f), TOLERANCE)
    }

    @Test
    fun aTurnIsNeverEmpty() {
        // A line height taller than the screen, or a step of nothing: a tap that
        // moved the book by zero would read as the reader having frozen.
        assertEquals(200f, pageTurnDistance(viewport, fraction = 1f, overlap = 5000f), TOLERANCE)
        assertEquals(200f, pageTurnDistance(viewport, fraction = 0f, overlap = overlap), TOLERANCE)
    }

    @Test
    fun aNegativeOverlapIsNoOverlap() {
        assertEquals(viewport, pageTurnDistance(viewport, fraction = 1f, overlap = -100f), TOLERANCE)
    }

    @Test
    fun withoutAViewportThereIsNoPage() {
        assertEquals(0f, pageTurnDistance(0f, fraction = 1f, overlap = overlap), TOLERANCE)
        assertEquals(0f, pageTurnDistance(-10f, fraction = 1f, overlap = overlap), TOLERANCE)
        assertEquals(0f, pageTurnDistance(Float.NaN, fraction = 1f, overlap = overlap), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
