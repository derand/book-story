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

private const val MINUTE = 60_000L

class ReadingPaceTest {

    @Test
    fun `words per minute over a real sitting`() {
        assertEquals(200, ReadingPace.wordsPerMinute(timeMs = 10 * MINUTE, words = 2000))
    }

    @Test
    fun `refuses a pace from too little time`() {
        assertNull(ReadingPace.wordsPerMinute(timeMs = 30_000, words = 300))
    }

    @Test
    fun `refuses a pace with nothing read`() {
        assertNull(ReadingPace.wordsPerMinute(timeMs = 10 * MINUTE, words = 0))
    }

    @Test
    fun `typical pace ignores a forgotten device`() {
        // Four ordinary sittings and one where the tablet was left open: the
        // median holds, a mean would be dragged down by a third.
        val typical = ReadingPace.typical(listOf(210, 190, 200, 205, 3))

        assertEquals(200, typical)
    }

    @Test
    fun `typical pace of an even number of sittings`() {
        assertEquals(195, ReadingPace.typical(listOf(190, 200, 210, 180)))
    }

    @Test
    fun `no sittings, no typical pace`() {
        assertNull(ReadingPace.typical(emptyList()))
        assertNull(ReadingPace.typical(listOf(0, -5)))
    }

    @Test
    fun `time for the words that are left`() {
        assertEquals(10 * MINUTE, ReadingPace.timeForWords(words = 2000, wordsPerMinute = 200))
    }

    @Test
    fun `no pace, no estimate`() {
        assertNull(ReadingPace.timeForWords(words = 2000, wordsPerMinute = null))
        assertNull(ReadingPace.timeForWords(words = 0, wordsPerMinute = 200))
    }

    @Test
    fun `finish date rounds up to whole days of reading`() {
        val now = 1_000_000_000L
        val day = 24 * 60 * 60 * 1000L

        // 90 minutes over 3 active days is 30 min a day; 61 minutes left is
        // three more days, not two and a bit.
        val by = ReadingPace.finishedBy(
            now = now,
            timeLeftMs = 61 * MINUTE,
            totalTimeMs = 90 * MINUTE,
            activeDays = 3
        )

        assertEquals(now + 3 * day, by)
    }

    @Test
    fun `no finish date without a day of evidence`() {
        assertNull(
            ReadingPace.finishedBy(
                now = 0,
                timeLeftMs = 60 * MINUTE,
                totalTimeMs = 30 * MINUTE,
                activeDays = 0
            )
        )
    }
}
