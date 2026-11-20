/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.SearchMatch

/** Search highlights: only the matches of this line's own part. */
class ReaderSearchHighlightTest {

    private val line = AnnotatedString("alpha beta gamma")

    private fun AnnotatedString.highlighted(): List<String> =
        spanStyles.map { text.substring(it.start, it.end) }

    @Test
    fun `only the matches of the given part are drawn`() {
        val matches = listOf(
            SearchMatch(itemIndex = 3, part = 0, start = 0, end = 5),
            SearchMatch(itemIndex = 3, part = 1, start = 6, end = 10),
            SearchMatch(itemIndex = 3, part = 2, start = 11, end = 16)
        )

        assertEquals(
            listOf("beta"),
            line.withSearchHighlights(matches, current = null, fontColor = Color.Black, part = 1)
                .highlighted()
        )
        assertEquals(
            listOf("alpha"),
            line.withSearchHighlights(matches, current = null, fontColor = Color.Black)
                .highlighted()
        )
    }

    @Test
    fun `the current match is drawn stronger than the rest`() {
        val current = SearchMatch(itemIndex = 0, start = 6, end = 10)
        val other = SearchMatch(itemIndex = 0, start = 0, end = 5)

        val result = line.withSearchHighlights(
            listOf(other, current), current = current, fontColor = Color.Black
        )

        val alphas = result.spanStyles.associate {
            line.text.substring(it.start, it.end) to it.item.background.alpha
        }
        assertTrue(alphas.getValue("beta") > alphas.getValue("alpha"))
    }

    @Test
    fun `a match past the end is clamped, an empty one dropped, none returns the line`() {
        val result = line.withSearchHighlights(
            listOf(
                SearchMatch(itemIndex = 0, start = 11, end = 99),
                SearchMatch(itemIndex = 0, start = 4, end = 4)
            ),
            current = null,
            fontColor = Color.Black
        )
        assertEquals(listOf("gamma"), result.highlighted())

        assertSame(
            line,
            line.withSearchHighlights(emptyList(), current = null, fontColor = Color.Black)
        )
    }
}
