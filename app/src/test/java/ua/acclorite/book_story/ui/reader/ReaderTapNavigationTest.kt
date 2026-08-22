/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which entry a press lands on, and which part of an image entry it is. */
class ReaderTapNavigationTest {

    private val spacing = 36f

    // Three entries of 200, laid out end to end from the top of the content.
    private val items = listOf(
        EntryBounds(index = 0, offset = 0, size = 200),
        EntryBounds(index = 1, offset = 200, size = 200),
        EntryBounds(index = 2, offset = 400, size = 200)
    )

    @Test
    fun aPressLandsOnTheEntryItIsOver() {
        assertEquals(EntryHit(1, 200f), entryHitAt(300f, items, spacing))
        assertEquals(EntryHit(2, 400f), entryHitAt(500f, items, spacing))
    }

    @Test
    fun theGapAboveAnEntryIsSharedWithTheOneAboveIt() {
        // The blank space belongs to the lower entry as far as the list is
        // concerned; its top half answers for the entry it visually touches.
        assertEquals(EntryHit(0, 0f), entryHitAt(210f, items, spacing))
        assertEquals(EntryHit(1, 200f), entryHitAt(230f, items, spacing))
    }

    @Test
    fun theFirstEntryKeepsItsOwnGap() {
        assertEquals(EntryHit(0, 0f), entryHitAt(5f, items, spacing))
    }

    @Test
    fun aPressOutsideEveryEntryLandsOnNothing() {
        assertNull(entryHitAt(-50f, items, spacing))
        assertNull(entryHitAt(600f, items, spacing))
        assertNull(entryHitAt(0f, emptyList(), spacing))
    }

    @Test
    fun theHalfGapRuleStillReachesAnEntryScrolledOffTheTop() {
        // Scrolled into entry 7, with 6 no longer measured: the press is still
        // handed to 6, and the only top on offer is 7's — which the picture
        // test then reads as "no caption here", the behaviour there was before.
        val scrolled = listOf(EntryBounds(index = 7, offset = -80, size = 200))

        assertEquals(EntryHit(6, -80f), entryHitAt(-70f, scrolled, spacing))
        assertEquals(EntryHit(7, -80f), entryHitAt(0f, scrolled, spacing))
    }

    @Test
    fun aPictureIsAsTallAsItsWidthAndRatioMakeIt() {
        // 1000 wide, 40 of padding on each side, 80% of what is left: 736 wide,
        // and a 2:1 picture is half that tall.
        assertEquals(
            368f,
            pictureHeight(entryWidth = 1000f, sidePadding = 40f, widthFraction = 0.8f, aspectRatio = 2f),
            TOLERANCE
        )
    }

    @Test
    fun aPictureWithNoRatioHasNoHeightToClaim() {
        // Better to treat the whole entry as caption than to open a viewer on a
        // press that was nowhere near a picture.
        assertEquals(0f, pictureHeight(1000f, 40f, 0.8f, 0f), TOLERANCE)
        assertEquals(0f, pictureHeight(1000f, 40f, 0.8f, Float.NaN), TOLERANCE)
        assertEquals(0f, pictureHeight(50f, 40f, 0.8f, 2f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
