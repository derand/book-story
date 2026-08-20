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
    private val line = 60f

    @Test
    fun aTurnIsAScreenfulLessTheOverlap() {
        assertEquals(1880f, pageTurnDistance(viewport, overlap = 2 * line), TOLERANCE)
    }

    @Test
    fun theOverlapIsTheOnlyThingThatShortensATurn() {
        // Every line asked for is a line the reader keeps, one for one.
        assertEquals(1940f, pageTurnDistance(viewport, overlap = line), TOLERANCE)
        assertEquals(1760f, pageTurnDistance(viewport, overlap = 4 * line), TOLERANCE)
    }

    @Test
    fun whatTheBottomEdgeCutComesBackWhole() {
        // The line the edge cut can be a full line height tall, so a page that
        // moves by this much can never carry text past the reader unseen.
        val distance = pageTurnDistance(viewport, overlap = line)

        assertEquals(viewport - distance, line, TOLERANCE)
    }

    @Test
    fun aTurnIsNeverEmpty() {
        // A line height taller than the screen: a tap that moved the book by
        // zero would read as the reader having frozen.
        assertEquals(200f, pageTurnDistance(viewport, overlap = 5000f), TOLERANCE)
    }

    @Test
    fun aNegativeOverlapIsNoOverlap() {
        assertEquals(viewport, pageTurnDistance(viewport, overlap = -100f), TOLERANCE)
    }

    @Test
    fun withoutAViewportThereIsNoPage() {
        assertEquals(0f, pageTurnDistance(0f, overlap = 2 * line), TOLERANCE)
        assertEquals(0f, pageTurnDistance(-10f, overlap = 2 * line), TOLERANCE)
        assertEquals(0f, pageTurnDistance(Float.NaN, overlap = 2 * line), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
