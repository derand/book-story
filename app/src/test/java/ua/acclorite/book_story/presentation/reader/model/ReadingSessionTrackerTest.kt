/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage

class ReadingSessionTrackerTest {

    /** Ten items of 10 words each; notes of 7 words. */
    private var now = 1_000L
    private val tracker = ReadingSessionTracker(
        itemWords = { index -> if (index in 0..9) 10 else null },
        noteWords = { 7 },
        clock = { now }
    )
    private val prefix = IntArray(11) { it * 10 }

    private fun at(index: Int) = Checkpoint(index = index, offset = 0)

    private fun loaded(covered: Set<Int> = emptySet()) {
        tracker.loadCoverage(
            stored = ReadingCoverage(
                bookId = 1,
                itemCount = 10,
                bookWords = 100,
                covered = covered,
                coveredWords = covered.size * 10,
                wordsBeforeBookmark = null
            ),
            itemCount = 10,
            prefix = prefix
        )
    }

    @Test
    fun `a session runs from start to stop`() {
        tracker.start(overlayShowing = false)
        assertTrue(tracker.isRunning)

        now = 5_000L
        val ended = tracker.end(bookId = 1, bookmarkIndex = 0)
        assertEquals(1_000L, ended?.startTime)
        assertEquals(5_000L, ended?.endTime)
        assertFalse(tracker.isRunning)
        assertNull(tracker.end(bookId = 1, bookmarkIndex = 0))
    }

    @Test
    fun `a second start does not restart the running session`() {
        tracker.start(overlayShowing = false)
        now = 3_000L
        tracker.start(overlayShowing = false)

        assertEquals(1_000L, tracker.end(bookId = 1, bookmarkIndex = 0)?.startTime)
    }

    @Test
    fun `nothing is credited outside a session`() {
        loaded()
        tracker.creditVisible(listOf(1, 2))
        tracker.noteOpened("n")
        tracker.creditOpenNote()

        tracker.start(overlayShowing = false)
        assertEquals(0, tracker.wordsRead)
        assertEquals(0f, tracker.coveragePercent)
    }

    @Test
    fun `nothing is credited before the coverage is loaded`() {
        tracker.start(overlayShowing = false)
        tracker.creditVisible(listOf(1, 2))
        assertEquals(0, tracker.wordsRead)

        loaded()
        tracker.creditVisible(listOf(1, 2))
        assertEquals(20, tracker.wordsRead)
    }

    @Test
    fun `a screen scrolled back to counts once`() {
        loaded(covered = setOf(1))
        tracker.start(overlayShowing = false)

        tracker.creditVisible(listOf(1, 2))
        tracker.creditVisible(listOf(2, 3))

        // Item 1 was covered before: volume again, coverage not.
        assertEquals(30, tracker.wordsRead)
        assertEquals(0.3f, tracker.coveragePercent)
        assertEquals(30, tracker.coverage(bookId = 1, bookmarkIndex = 0)?.coveredWords)
    }

    @Test
    fun `items past the text are skipped`() {
        loaded()
        tracker.start(overlayShowing = false)
        tracker.creditVisible(listOf(9, 10))

        assertEquals(10, tracker.wordsRead)
    }

    @Test
    fun `the landing after a jump is not credited, the next screen is`() {
        loaded()
        tracker.start(overlayShowing = false)

        tracker.jumped(from = at(0), to = at(5))
        tracker.settled(listOf(5))
        assertEquals(0, tracker.wordsRead)

        tracker.settled(listOf(6))
        assertEquals(10, tracker.wordsRead)
    }

    @Test
    fun `a jump to where the list already stands does not swallow the next screen`() {
        // The slider released twice on one spot: no sample comes for the second.
        loaded()
        tracker.start(overlayShowing = false)

        tracker.jumped(from = at(5), to = at(5))

        tracker.settled(listOf(6))
        assertEquals(10, tracker.wordsRead)
    }

    @Test
    fun `a jump the list normalises back is still a landing`() {
        // A search step to the next match in the same paragraph: the request
        // (item 5, pushed down) differs from the position (item 4, scrolled),
        // so a sample does come, at the old position, and must not be credited.
        loaded()
        tracker.start(overlayShowing = false)

        tracker.jumped(from = Checkpoint(index = 4, offset = 120), to = Checkpoint(index = 5, offset = -300))
        tracker.settled(listOf(4, 5))
        assertEquals(0, tracker.wordsRead)
    }

    @Test
    fun `a settled position is a sign of life`() {
        tracker.start(overlayShowing = false)
        now = 4_000L
        tracker.settled(emptyList())

        assertEquals(4_000L, tracker.lastActive)
    }

    @Test
    fun `overlay time is banked when it is hidden`() {
        tracker.start(overlayShowing = false)
        now = 2_000L
        tracker.overlayShown()
        now = 2_500L
        // Something else covering the text too does not restart the span.
        tracker.overlayShown()
        now = 3_000L
        tracker.overlayHidden()

        assertEquals(1_000L, tracker.overlayMs)
        assertEquals(3_000L, tracker.lastActive)
    }

    @Test
    fun `the end closes the overlay without moving the last sign of life`() {
        tracker.start(overlayShowing = false)
        now = 2_000L
        tracker.overlayShown()
        now = 9_000L
        val ended = tracker.end(bookId = 1, bookmarkIndex = 0)

        assertEquals(7_000L, ended?.overlayMs)
        assertEquals(2_000L, ended?.lastActive)
    }

    @Test
    fun `a session started under an overlay counts it from the start`() {
        tracker.start(overlayShowing = true)
        now = 1_600L
        tracker.overlayHidden()

        assertEquals(600L, tracker.overlayMs)
    }

    @Test
    fun `a note is credited when it closes, once`() {
        tracker.start(overlayShowing = false)
        tracker.noteOpened("n")
        assertEquals(0, tracker.wordsRead)

        tracker.creditOpenNote()
        tracker.creditOpenNote()
        assertEquals(7, tracker.wordsRead)
    }

    @Test
    fun `a note still open is credited by the end`() {
        tracker.start(overlayShowing = false)
        tracker.noteOpened("n")

        assertEquals(7, tracker.end(bookId = 1, bookmarkIndex = 0)?.wordsRead)
    }

    @Test
    fun `the coverage carries the words before the bookmark, clamped`() {
        loaded()

        assertEquals(30, tracker.coverage(bookId = 1, bookmarkIndex = 3)?.wordsBeforeBookmark)
        assertEquals(100, tracker.coverage(bookId = 1, bookmarkIndex = 99)?.wordsBeforeBookmark)
    }

    @Test
    fun `no coverage to store before it is loaded or after a reset`() {
        assertNull(tracker.coverage(bookId = 1, bookmarkIndex = 0))
        loaded()
        tracker.reset()
        assertNull(tracker.coverage(bookId = 1, bookmarkIndex = 0))
    }

    @Test
    fun `the end of the book is carried to the session that reached it`() {
        tracker.start(overlayShowing = false)
        tracker.progressed(0.5f)
        assertFalse(tracker.reachedEnd)

        tracker.progressed(1f)
        assertTrue(tracker.reachedEnd)

        assertEquals(true, tracker.end(bookId = 1, bookmarkIndex = 0)?.reachedEnd)
        assertFalse(tracker.reachedEnd)
        assertEquals(0, tracker.wordsRead)
    }

    @Test
    fun `the end hands over the coverage as it stood`() {
        loaded()
        tracker.start(overlayShowing = false)
        tracker.settled(listOf(1, 2))

        val ended = tracker.end(bookId = 7, bookmarkIndex = 3)
        assertEquals(setOf(1, 2), ended?.coverage?.covered)
        assertEquals(7, ended?.coverage?.bookId)
        assertEquals(30, ended?.coverage?.wordsBeforeBookmark)
        assertEquals(0.2f, ended?.coveragePercent)
    }

    @Test
    fun `a session started while the last one is written keeps to itself`() {
        // ON_STOP, then ON_START before the writes of the first end are done.
        loaded()
        tracker.start(overlayShowing = false)
        tracker.settled(listOf(1))
        now = 2_000L
        tracker.overlayShown()
        now = 3_000L
        val first = tracker.end(bookId = 1, bookmarkIndex = 0)

        now = 3_100L
        tracker.start(overlayShowing = false)
        tracker.settled(listOf(2, 3))

        assertEquals(10, first?.wordsRead)
        assertEquals(1_000L, first?.overlayMs)
        assertEquals(2_000L, first?.lastActive)
        assertEquals(setOf(1), first?.coverage?.covered)

        val second = tracker.end(bookId = 1, bookmarkIndex = 0)
        assertEquals(20, second?.wordsRead)
        assertEquals(0L, second?.overlayMs)
        assertEquals(3_100L, second?.startTime)
    }

    @Test
    fun `a preview read to its end does not finish the next book`() {
        // A preview runs no session; the reader still reports its progress.
        loaded()
        tracker.settled(listOf(9))
        tracker.progressed(1f)
        tracker.reset()

        loaded()
        tracker.start(overlayShowing = false)
        tracker.settled(listOf(0))
        tracker.progressed(0.1f)

        assertFalse(tracker.reachedEnd)
    }

    @Test
    fun `leaving the book forgets that it was finished`() {
        loaded()
        tracker.start(overlayShowing = false)
        tracker.settled(listOf(9))
        tracker.progressed(1f)
        tracker.end(bookId = 1, bookmarkIndex = 0)
        tracker.reset()

        tracker.start(overlayShowing = false)
        assertFalse(tracker.reachedEnd)
    }

    @Test
    fun `a slider dropped at the end does not finish the book`() {
        loaded()
        tracker.start(overlayShowing = false)

        tracker.jumped(from = at(0), to = at(5))
        tracker.settled(listOf(9))
        tracker.progressed(1f)
        assertFalse(tracker.reachedEnd)

        // Reading on from there is reading the end.
        tracker.settled(listOf(8, 9))
        tracker.progressed(1f)
        assertTrue(tracker.reachedEnd)
    }
}
