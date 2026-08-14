/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun coverage(
    itemCount: Int = 100,
    bookWords: Int = 1_000,
    covered: Set<Int> = setOf(1, 2, 3),
    coveredWords: Int = 30,
    wordsBeforeBookmark: Int? = 900
) = ReadingCoverage(
    bookId = 1,
    itemCount = itemCount,
    bookWords = bookWords,
    covered = covered,
    coveredWords = coveredWords,
    wordsBeforeBookmark = wordsBeforeBookmark
)

class ReadingCoverageTest {

    @Test
    fun `a matching item count keeps everything`() {
        val stored = coverage(itemCount = 100)

        assertSame(stored, stored.validFor(100))
    }

    @Test
    fun `a reparse drops the intervals and the bookmark's words`() {
        // Both were counted against indices that now point elsewhere. The
        // book's own word total survives: it is a fact about the text, not
        // about any position in it.
        val stale = coverage(itemCount = 100).validFor(120)

        assertEquals(120, stale.itemCount)
        assertTrue(stale.covered.isEmpty())
        assertEquals(0, stale.coveredWords)
        assertNull(stale.wordsBeforeBookmark)
        assertEquals(1_000, stale.bookWords)
    }

    @Test
    fun `a book never read yet knows no bookmark`() {
        val empty = ReadingCoverage.empty(bookId = 1, itemCount = 100, bookWords = 1_000)

        assertNull(empty.wordsBeforeBookmark)
        assertEquals(0f, empty.percent, 0f)
    }

    @Test
    fun `percent is items covered, not words`() {
        assertEquals(0.03f, coverage(itemCount = 100, covered = setOf(1, 2, 3)).percent, 0.001f)
    }

    @Test
    fun `a text with no items is not a division by zero`() {
        assertEquals(0f, coverage(itemCount = 0, covered = emptySet()).percent, 0f)
    }
}
