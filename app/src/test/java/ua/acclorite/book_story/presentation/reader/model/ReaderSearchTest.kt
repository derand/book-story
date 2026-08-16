/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import org.junit.Test

/**
 * The two rules the search bar is built on: what the counter says about where
 * the reader stands, and where an arrow goes from there — backwards by default,
 * because a reader searches what they have already read.
 */
class ReaderSearchTest {

    /** Matches at items 10, 20 and 30 — one per paragraph. */
    private val matches = listOf(10, 20, 30).map { index ->
        SearchMatch(itemIndex = index, start = 0, end = 4)
    }

    private fun search(current: Int = -1) = ReaderSearch(
        active = true,
        query = "lamp",
        matches = matches,
        current = current
    )

    @Test
    fun `no query means nothing to count`() {
        assertEquals(
            SearchPosition.None,
            searchPosition(matches = emptyList(), current = -1, visible = 0..5)
        )
    }

    @Test
    fun `the match jumped to is the one counted`() {
        assertEquals(
            SearchPosition.At(2),
            searchPosition(matches = matches, current = 1, visible = 0..5)
        )
    }

    @Test
    fun `a match on screen is counted before any jump`() {
        assertEquals(
            SearchPosition.At(2),
            searchPosition(matches = matches, current = -1, visible = 18..24)
        )
    }

    @Test
    fun `between two matches, the counter says so`() {
        assertEquals(
            SearchPosition.Between(2),
            searchPosition(matches = matches, current = -1, visible = 24..28)
        )
    }

    @Test
    fun `before the first match, nothing is behind`() {
        assertEquals(
            SearchPosition.Between(0),
            searchPosition(matches = matches, current = -1, visible = 0..5)
        )
    }

    @Test
    fun `past the last match, all of them are behind`() {
        assertEquals(
            SearchPosition.Between(3),
            searchPosition(matches = matches, current = -1, visible = 40..45)
        )
    }

    @Test
    fun `entering the search goes to the closest match behind the screen`() {
        assertEquals(1, search().stepTarget(forward = false, visible = 24..28))
    }

    @Test
    fun `entering forwards goes to the closest match past the screen`() {
        assertEquals(2, search().stepTarget(forward = true, visible = 24..28))
    }

    @Test
    fun `a match already on screen is not somewhere to go`() {
        // Item 20 is in front of the reader: back goes to 10, forward to 30.
        assertEquals(0, search().stepTarget(forward = false, visible = 18..22))
        assertEquals(2, search().stepTarget(forward = true, visible = 18..22))
    }

    @Test
    fun `stepping from a match is one match at a time`() {
        val search = search(current = 1)
        assertEquals(0, search.stepTarget(forward = false, visible = 18..22))
        assertEquals(2, search.stepTarget(forward = true, visible = 18..22))
    }

    @Test
    fun `two matches in one paragraph are two stops`() {
        val crowded = ReaderSearch(
            active = true,
            query = "lamp",
            matches = listOf(
                SearchMatch(itemIndex = 10, start = 0, end = 4),
                SearchMatch(itemIndex = 10, start = 40, end = 44)
            ),
            current = 0
        )

        assertEquals(1, crowded.stepTarget(forward = true, visible = 10..12))
    }

    @Test
    fun `there is nowhere to go past the ends`() {
        assertNull(search(current = 0).stepTarget(forward = false, visible = 10..12))
        assertNull(search(current = 2).stepTarget(forward = true, visible = 30..32))
        // And the same entering from reading, with the whole book behind.
        assertNull(search().stepTarget(forward = true, visible = 40..45))
        assertNull(search().stepTarget(forward = false, visible = 0..5))
    }

    @Test
    fun `an empty result has no target either way`() {
        val empty = ReaderSearch(active = true, query = "lamp")
        assertNull(empty.stepTarget(forward = true, visible = 0..5))
        assertNull(empty.stepTarget(forward = false, visible = 0..5))
    }
}
