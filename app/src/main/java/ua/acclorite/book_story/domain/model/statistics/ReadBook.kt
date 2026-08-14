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
)

/*
 * There is deliberately no pace on this row. [totalTimeMs] is time spent with
 * the book, overlay time included, and this row does not carry the overlay
 * separately to take it back out — so a pace divided from it would be the one
 * figure the image viewer could still distort. A book's pace is a median over
 * its sessions instead, where the overlay is known.
 */
