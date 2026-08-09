/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

/**
 * Which parts of a book have been read, as opposed to where its bookmark sits.
 *
 * [covered] holds item indices, so it is only meaningful for the parse it was
 * recorded against — [itemCount] is the sentinel that says so.
 */
data class ReadingCoverage(
    val bookId: Int,
    val itemCount: Int,
    /** Words in the whole book, for "how much is left". */
    val bookWords: Int,
    val covered: Set<Int>,
    /** Words inside [covered] — unique, unlike a session's `wordsRead`. */
    val coveredWords: Int,
    /**
     * Words before the bookmark, for "how much is left to read". Deliberately
     * *not* derived from [coveredWords]: what is ahead of the reader and what
     * the measurement has never seen are different questions, and only the
     * first one is a forecast. A book whose statistics began mid-way has most
     * of itself uncovered while having almost nothing left.
     *
     * Null until the book is opened once with this recorded, and the card then
     * omits the figure rather than approximating it.
     */
    val wordsBeforeBookmark: Int?
) {
    val percent: Float
        get() = if (itemCount <= 0) 0f else covered.size.toFloat() / itemCount

    /**
     * The coverage to start from for a book whose text now has [itemCount]
     * items. A parser change shifts every index, so on a mismatch the intervals
     * are dropped — and with them [wordsBeforeBookmark], which was counted up to
     * an index that now points elsewhere. Only those: what the sessions have
     * already banked is history and is not reachable from here.
     */
    fun validFor(itemCount: Int): ReadingCoverage =
        if (this.itemCount == itemCount) this
        else copy(
            itemCount = itemCount,
            covered = emptySet(),
            coveredWords = 0,
            wordsBeforeBookmark = null
        )

    companion object {
        fun empty(bookId: Int, itemCount: Int, bookWords: Int) = ReadingCoverage(
            bookId = bookId,
            itemCount = itemCount,
            bookWords = bookWords,
            covered = emptySet(),
            coveredWords = 0,
            wordsBeforeBookmark = null
        )
    }
}
