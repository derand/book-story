/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BOOK_ID = 7
private const val START = 1_000_000L

class ReadingSessionTest {

    private fun session(
        lastActiveTime: Long,
        now: Long,
        wordsRead: Int = 0,
        overlayMs: Long = 0
    ) = ReadingSession.endedAt(
        bookId = BOOK_ID,
        startTime = START,
        lastActiveTime = lastActiveTime,
        now = now,
        wordsRead = wordsRead,
        overlayMs = overlayMs
    )

    @Test
    fun `records a session that was read`() {
        val read = 30_000L
        val session = session(lastActiveTime = START + read, now = START + read)

        assertEquals(read, session?.durationMs)
        assertEquals(BOOK_ID, session?.bookId)
    }

    @Test
    fun `drops an open and close`() {
        assertNull(session(lastActiveTime = START, now = START + 4_999))
    }

    @Test
    fun `keeps a session exactly at the minimum`() {
        val session = session(lastActiveTime = START, now = START + ReadingSession.MIN_DURATION_MS)

        assertEquals(ReadingSession.MIN_DURATION_MS, session?.durationMs)
    }

    @Test
    fun `caps the idle left after the last settled position`() {
        val lastActive = START + 60_000
        val session = session(lastActiveTime = lastActive, now = lastActive + 3 * 60 * 60 * 1000)

        assertEquals(
            60_000 + ReadingSession.IDLE_CAP_MS,
            session?.durationMs
        )
    }

    @Test
    fun `counts idle inside a sitting in full`() {
        // Scrolled at the very end after a long pause: nothing to cap, because
        // the pause is not trailing. Staring at one page is still reading.
        val end = START + 40 * 60 * 1000
        val session = session(lastActiveTime = end, now = end)

        assertEquals(40 * 60 * 1000L, session?.durationMs)
    }

    @Test
    fun `a session shorter than the minimum only because of the cap is dropped`() {
        // Opened, one settled position, then left open for hours: the cap pulls
        // the end back to a point that is still a real session.
        val lastActive = START + 1_000
        val session = session(lastActiveTime = lastActive, now = lastActive + 86_400_000)

        assertEquals(1_000 + ReadingSession.IDLE_CAP_MS, session?.durationMs)
    }

    @Test
    fun `never ends before it started`() {
        // A clock moved backwards must not produce a negative session.
        val session = session(lastActiveTime = START - 600_000, now = START - 300_000)

        assertNull(session)
    }

    @Test
    fun `overlay time is kept in the book's time and taken out of the reading time`() {
        val session = session(
            lastActiveTime = START + 20 * 60 * 1000,
            now = START + 20 * 60 * 1000,
            overlayMs = 8 * 60 * 1000
        )

        // Time in the book keeps the whole interval — looking at an
        // illustration is time spent with the book.
        assertEquals(20 * 60 * 1000L, session?.durationMs)
        // The speed divides by the part in which words were possible.
        assertEquals(12 * 60 * 1000L, session?.readingMs)
    }

    @Test
    fun `a session without an overlay reads for the whole of it`() {
        val session = session(lastActiveTime = START + 30_000, now = START + 30_000)

        assertEquals(session?.durationMs, session?.readingMs)
    }

    @Test
    fun `an overlay cannot outlast the session the cap left`() {
        // Left in the image viewer and never came back: the tail is capped, and
        // the overlay it was holding is longer than what survives the cap.
        val lastActive = START + 1_000
        val session = session(
            lastActiveTime = lastActive,
            now = lastActive + 3 * 60 * 60 * 1000,
            overlayMs = 3 * 60 * 60 * 1000
        )

        assertEquals(1_000 + ReadingSession.IDLE_CAP_MS, session?.durationMs)
        assertEquals(1_000 + ReadingSession.IDLE_CAP_MS, session?.overlayMs)
        assertEquals(0L, session?.readingMs)
    }

    @Test
    fun `carries the words it was given`() {
        val session = session(lastActiveTime = START + 30_000, now = START + 30_000, wordsRead = 412)

        assertEquals(412, session?.wordsRead)
    }
}
