/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.runtime.Immutable

/**
 * Reading across the whole library, and across books that are no longer in it.
 *
 * Every figure is aggregated from the session rows, anonymised ones included, so
 * deleting a book cannot retroactively shrink the total or shorten the days —
 * which also means these totals will not add up to the sum of the per-book cards
 * once anything has been deleted. That is worth saying in the UI rather than
 * hiding.
 */
@Immutable
data class LibraryStatistics(
    val totalTimeMs: Long,
    val wordsRead: Long,
    /** Median across sessions; null until there is a minute to divide. */
    val wordsPerMinute: Int?,
    /** Books the reader marked, or reaching the end marked, as finished. */
    val booksRead: Int,
    /** Days with any reading at all. Not consecutive — nothing to break. */
    val daysRead: Int
) {
    val isEmpty: Boolean get() = totalTimeMs == 0L && daysRead == 0

    companion object {
        val none = LibraryStatistics(
            totalTimeMs = 0,
            wordsRead = 0,
            wordsPerMinute = null,
            booksRead = 0,
            daysRead = 0
        )
    }
}
