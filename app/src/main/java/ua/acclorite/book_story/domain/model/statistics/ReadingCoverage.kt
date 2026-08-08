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
    val coveredWords: Int
) {
    val percent: Float
        get() = if (itemCount <= 0) 0f else covered.size.toFloat() / itemCount

    /**
     * The coverage to start from for a book whose text now has [itemCount]
     * items. A parser change shifts every index, so on a mismatch the intervals
     * are dropped — and only they: what the sessions have already banked is
     * history and is not reachable from here.
     */
    fun validFor(itemCount: Int): ReadingCoverage =
        if (this.itemCount == itemCount) this
        else copy(itemCount = itemCount, covered = emptySet(), coveredWords = 0)

    companion object {
        fun empty(bookId: Int, itemCount: Int, bookWords: Int) = ReadingCoverage(
            bookId = bookId,
            itemCount = itemCount,
            bookWords = bookWords,
            covered = emptySet(),
            coveredWords = 0
        )
    }
}
