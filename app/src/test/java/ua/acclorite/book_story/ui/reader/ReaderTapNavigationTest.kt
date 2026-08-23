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

    /** No chapter ends on any of these entries. */
    private val noBreaks: (Int) -> Float = { 0f }

    /** A chapter ends on entry 1, leaving 60 of blank space below it. */
    private val breakAfterOne: (Int) -> Float = { if (it == 1) 60f else 0f }

    // Three entries of 200, laid out end to end from the top of the content.
    private val items = listOf(
        EntryBounds(index = 0, offset = 0, size = 200),
        EntryBounds(index = 1, offset = 200, size = 200),
        EntryBounds(index = 2, offset = 400, size = 200)
    )

    @Test
    fun aPressLandsOnTheEntryItIsOver() {
        assertEquals(EntryHit(1, 200f), entryHitAt(300f, items, spacing, noBreaks))
        assertEquals(EntryHit(2, 400f), entryHitAt(500f, items, spacing, noBreaks))
    }

    @Test
    fun theGapAboveAnEntryIsSharedWithTheOneAboveIt() {
        // The blank space belongs to the lower entry as far as the list is
        // concerned; its top half answers for the entry it visually touches.
        assertEquals(EntryHit(0, 0f), entryHitAt(210f, items, spacing, noBreaks))
        assertEquals(EntryHit(1, 200f), entryHitAt(230f, items, spacing, noBreaks))
    }

    @Test
    fun theFirstEntryKeepsItsOwnGap() {
        assertEquals(EntryHit(0, 0f), entryHitAt(5f, items, spacing, noBreaks))
    }

    @Test
    fun aPressOutsideEveryEntryLandsOnNothing() {
        assertNull(entryHitAt(-50f, items, spacing, noBreaks))
        assertNull(entryHitAt(600f, items, spacing, noBreaks))
        assertNull(entryHitAt(0f, emptyList(), spacing, noBreaks))
    }

    @Test
    fun theHalfGapRuleStillReachesAnEntryScrolledOffTheTop() {
        // Scrolled into entry 7, with 6 no longer measured: the press is still
        // handed to 6, and the only top on offer is 7's — which the picture
        // test then reads as "no caption here", the behaviour there was before.
        val scrolled = listOf(EntryBounds(index = 7, offset = -80, size = 200))

        assertEquals(EntryHit(6, -80f), entryHitAt(-70f, scrolled, spacing, noBreaks))
        assertEquals(EntryHit(7, -80f), entryHitAt(0f, scrolled, spacing, noBreaks))
    }

    @Test
    fun theBreakClosingAChapterBelongsToNoEntry() {
        // Entry 1 runs 200..400 and the last 60 of it is the boundary: its own
        // content stops at 340. A press below that is a press on the page, so
        // the picture that closed the chapter cannot be opened from under it.
        assertNull(entryHitAt(340f, items, spacing, breakAfterOne))
        assertNull(entryHitAt(399f, items, spacing, breakAfterOne))
        assertEquals(EntryHit(1, 200f), entryHitAt(339f, items, spacing, breakAfterOne))
    }

    @Test
    fun theHalfGapBelowABreakIsBoundaryToo() {
        // The gap is laid out inside entry 2, and its upper half would normally
        // answer for entry 1 — but entry 1's chapter has already ended, so that
        // strip is boundary as well. Below the half-way line it is entry 2.
        assertNull(entryHitAt(400f, items, spacing, breakAfterOne))
        assertNull(entryHitAt(417f, items, spacing, breakAfterOne))
        assertEquals(EntryHit(2, 400f), entryHitAt(418f, items, spacing, breakAfterOne))
    }

    @Test
    fun onlyTheEntryThatEndsAChapterLosesItsBottom() {
        // Entry 2's bottom is still entry 2's, and the gap above entry 1 still
        // answers for entry 0 — a break is asked for per index, not per list.
        assertEquals(EntryHit(2, 400f), entryHitAt(599f, items, spacing, breakAfterOne))
        assertEquals(EntryHit(0, 0f), entryHitAt(210f, items, spacing, breakAfterOne))
    }

    @Test
    fun aBreakOnAnEntryScrolledOffTheTopStillSwallowsTheHalfGap() {
        // Entry 6 is no longer measured, so how far its content reaches is not
        // knowable — and the press is inside the gap that follows its break.
        val scrolled = listOf(EntryBounds(index = 7, offset = -80, size = 200))

        assertNull(entryHitAt(-70f, scrolled, spacing) { if (it == 6) 60f else 0f })
        assertEquals(EntryHit(7, -80f), entryHitAt(0f, scrolled, spacing) { 0f })
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
