/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Which parts of a book have actually been read, as opposed to where its
 * bookmark sits. Open a three-novel omnibus at the last novel and read it to the
 * end: the bookmark says 100 %, correctly, but only a third was read. Progress
 * deltas would credit the jump; these intervals do not.
 *
 * One row per book, dropped when the book is deleted — without the text the
 * indices mean nothing. [ReadBookEntity.coveragePercent] keeps the final figure.
 */
@Entity
data class ReadingCoverageEntity(
    @PrimaryKey
    val bookId: Int,
    /**
     * Item count of the parse these intervals were recorded against. A parser
     * change shifts every index, so on a mismatch the intervals are discarded —
     * and *only* the intervals: the words and time already banked in the
     * sessions are history and are never touched.
     */
    val itemCount: Int,
    /** Total words of the book's text, for "time left". */
    val bookWords: Int,
    /** Covered item ranges, `"12-480,3900-5200"`. */
    val intervals: String,
    /** Words inside [intervals] — unique, unlike a session's `wordsRead`. */
    val coveredWords: Int,
    /**
     * Words before the bookmark, for "left to read". Nullable rather than
     * defaulted: a row written before this column existed does not know where
     * its bookmark stood, and 0 would claim the whole book is still ahead.
     */
    val wordsBeforeBookmark: Int?
)
