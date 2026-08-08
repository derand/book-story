/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.runtime.Immutable

/**
 * A book's own reading record, kept for as long as the statistics are — the
 * shelf of everything ever read. Unlike the sessions it is built from, it
 * survives the book being deleted: the row is unlinked, not removed.
 */
@Immutable
data class ReadBook(
    val id: Int,
    /** The live book, or null once it has been deleted from the library. */
    val bookId: Int?,
    val title: String,
    val author: String,
    val totalTimeMs: Long,
    val totalWords: Int,
    val sessions: Int,
    val firstReadAt: Long,
    val lastReadAt: Long,
    val finished: Boolean,
    val coveragePercent: Float
) {
    /**
     * Words per minute over everything ever read of this book. Null until there
     * is enough to divide by — a first short sitting says nothing about pace.
     */
    val wordsPerMinute: Int?
        get() {
            if (totalTimeMs < MIN_TIME_FOR_PACE_MS || totalWords <= 0) return null
            return (totalWords * 60_000.0 / totalTimeMs).toInt()
        }

    companion object {
        /** Below this a pace is noise: one page and a distraction. */
        const val MIN_TIME_FOR_PACE_MS = 60_000L
    }
}
