/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.runtime.Immutable

/**
 * One stretch of reading: from the text becoming visible to the reader being
 * left or stopped. Built through [endedAt], which is where the two rules about
 * what counts live.
 */
@Immutable
data class ReadingSession(
    val bookId: Int,
    val startTime: Long,
    val endTime: Long,
    /** Words credited to this session — the volume read, repeats included. */
    val wordsRead: Int
) {
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0)

    companion object {
        /** Anything shorter is an open-and-close, not reading. */
        const val MIN_DURATION_MS = 5_000L

        /**
         * How much idle is kept after the last sign of life. Only the *tail* is
         * capped: a book left open overnight stops counting five minutes after
         * the last scroll, while a pause in the middle of a sitting is counted
         * in full — staring at one page for a while is still reading.
         */
        const val IDLE_CAP_MS = 5 * 60 * 1000L

        /**
         * The session that ran from [startTime] to [now], or null if it is too
         * short to mean anything. [lastActiveTime] is when the reader last
         * settled on a position; it caps the trailing idle.
         */
        fun endedAt(
            bookId: Int,
            startTime: Long,
            lastActiveTime: Long,
            now: Long,
            wordsRead: Int
        ): ReadingSession? {
            val endTime = now
                .coerceAtMost(lastActiveTime + IDLE_CAP_MS)
                .coerceAtLeast(startTime)

            return ReadingSession(
                bookId = bookId,
                startTime = startTime,
                endTime = endTime,
                wordsRead = wordsRead
            ).takeIf { it.durationMs >= MIN_DURATION_MS }
        }
    }
}
