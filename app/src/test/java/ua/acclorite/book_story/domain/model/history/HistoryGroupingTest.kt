/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.history

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The History list: sessions grouped by the day they started, then by book. */
class HistoryGroupingTest {

    private val zone = ZoneId.of("Europe/Kyiv")
    private val bookA = Book.default.copy(id = 1, title = "A")
    private val bookB = Book.default.copy(id = 2, title = "B")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(book: Book, start: Long, end: Long, words: Int = 100) =
        HistorySession(book, ReadingSession(book.id, start, end, words))

    private fun day(day: Int) = LocalDate.of(2026, 11, day)

    @Test
    fun `sessions of one book on one day add up to one entry`() {
        val grouped = groupHistory(
            listOf(
                session(bookA, at(18, 9), at(18, 9, 30), words = 300),
                session(bookA, at(18, 21), at(18, 21, 10), words = 50)
            ),
            zone
        )

        assertEquals(
            listOf(
                GroupedHistory(
                    day(18),
                    listOf(History(bookA, day(18), at(18, 21, 10), 40 * 60_000L, 350, sessions = 2))
                )
            ),
            grouped
        )
    }

    @Test
    fun `newest day first, and within a day the book read last first`() {
        val grouped = groupHistory(
            listOf(
                session(bookA, at(17, 10), at(17, 11)),
                session(bookA, at(18, 8), at(18, 9)),
                session(bookB, at(18, 12), at(18, 13)),
                session(bookB, at(17, 7), at(17, 8))
            ),
            zone
        )

        assertEquals(
            listOf(day(18) to listOf(2, 1), day(17) to listOf(1, 2)),
            grouped.map { group -> group.day to group.history.map { it.book.id } }
        )
        assertEquals(listOf(1, 1, 1, 1), grouped.flatMap { it.history }.map { it.sessions })
    }

    @Test
    fun `a session past midnight belongs to the day it started`() {
        val grouped = groupHistory(
            listOf(session(bookA, at(17, 23, 40), at(18, 0, 20))),
            zone
        )

        assertEquals(listOf(day(17)), grouped.map { it.day })
        assertEquals(40 * 60_000L, grouped.single().history.single().timeMs)
    }

    @Test
    fun `the day is the reader's local day, not UTC's`() {
        // 01:30 in Kyiv is still the previous day in UTC.
        val grouped = groupHistory(listOf(session(bookA, at(18, 1, 30), at(18, 2))), zone)

        assertEquals(listOf(day(18)), grouped.map { it.day })
    }
}
