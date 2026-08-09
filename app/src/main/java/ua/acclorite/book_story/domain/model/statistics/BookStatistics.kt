/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.runtime.Immutable

/**
 * What the book's card shows. Every figure that cannot be trusted yet is null
 * rather than zero, so the card can leave it out instead of stating something
 * false.
 */
@Immutable
data class BookStatistics(
    val totalTimeMs: Long,
    val sessions: Int,
    val finished: Boolean,
    /** Share of the book actually read — not where the bookmark sits. */
    val coveragePercent: Float,
    val bookWords: Int,
    val coveredWords: Int,
    /** Pace in this book, and across all reading, for comparison. */
    val wordsPerMinute: Int?,
    val typicalWordsPerMinute: Int?,
    val timeLeftMs: Long?,
    val finishedBy: Long?
) {
    /** Nothing has been read here yet, so the card has nothing to say. */
    val isEmpty: Boolean get() = sessions == 0 && totalTimeMs == 0L

    companion object {
        val none = BookStatistics(
            totalTimeMs = 0,
            sessions = 0,
            finished = false,
            coveragePercent = 0f,
            bookWords = 0,
            coveredWords = 0,
            wordsPerMinute = null,
            typicalWordsPerMinute = null,
            timeLeftMs = null,
            finishedBy = null
        )
    }
}
