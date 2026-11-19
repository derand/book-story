/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.history

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One reading session, with the book it was read in. */
data class HistorySession(
    val book: Book,
    val session: ReadingSession
)

/** One book on one day: what its sessions that day add up to. */
@Immutable
data class History(
    val book: Book,
    val day: LocalDate,
    /** End of the day's last session with this book. */
    val lastReadAt: Long,
    /** The sessions' time in the book, as the statistics count it. */
    val timeMs: Long,
    val words: Int,
    /** How many sessions the entry adds up. */
    val sessions: Int
)

/** A day of the History list, its books by when they were last read. */
@Immutable
data class GroupedHistory(
    val day: LocalDate,
    val history: List<History>
)

/**
 * The History list: [sessions] grouped by day, newest day first, then by book
 * within a day, the book read last first.
 *
 * A session belongs to the day it started on in [zone], the same rule as the
 * statistics' active days; one that runs past midnight is not split.
 */
fun groupHistory(sessions: List<HistorySession>, zone: ZoneId): List<GroupedHistory> =
    sessions
        .groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(zone).toLocalDate() }
        .toSortedMap(reverseOrder())
        .map { (day, daySessions) ->
            GroupedHistory(
                day = day,
                history = daySessions
                    .groupBy { it.book.id }
                    .values
                    .map { bookSessions ->
                        val last = bookSessions.maxBy { it.session.endTime }
                        History(
                            book = last.book,
                            day = day,
                            lastReadAt = last.session.endTime,
                            timeMs = bookSessions.sumOf { it.session.durationMs },
                            words = bookSessions.sumOf { it.session.wordsRead },
                            sessions = bookSessions.size
                        )
                    }
                    .sortedByDescending { it.lastReadAt }
            )
        }
