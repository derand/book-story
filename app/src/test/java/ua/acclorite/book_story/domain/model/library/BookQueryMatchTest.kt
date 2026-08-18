/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.ui.UIText

/**
 * What a search query means for a book. One rule for the library and the
 * history, because two rules is how the same query came to give two answers.
 */
class BookQueryMatchTest {

    private fun book(
        title: String,
        author: String? = null,
        description: String? = null
    ) = Book.default.copy(
        title = title,
        author = author?.let { UIText.StringValue(it) }
            ?: UIText.StringResource(R.string.unknown_author),
        description = description
    )

    private fun matches(query: String, book: Book) = book.matchesSearch(bookSearchTokens(query))

    private val solaris = book(
        title = "Соляріс",
        author = "Станіслав Лем",
        description = "Планета, вкрита мислячим океаном."
    )

    @Test
    fun `matches the title`() {
        assertTrue(matches("соляр", solaris))
    }

    @Test
    fun `matches the author`() {
        assertTrue(matches("лем", solaris))
    }

    /**
     * The reason the filter left SQL: `LOWER()` in SQLite folds ASCII and
     * nothing else, so this exact query used to return nothing.
     */
    @Test
    fun `case is ignored beyond ASCII`() {
        assertTrue(matches("соляріс", book(title = "Соляріс")))
        assertTrue(matches("СОЛЯРІС", book(title = "соляріс")))
        assertTrue(matches("solaris", book(title = "SOLARIS")))
    }

    /** The other half of leaving SQL: these are characters, not wildcards. */
    @Test
    fun `LIKE wildcards are literal`() {
        assertFalse(matches("%", solaris))
        assertFalse(matches("_", solaris))
        assertTrue(matches("%", book(title = "100% Rust")))
        assertTrue(matches("_", book(title = "snake_case")))
    }

    @Test
    fun `every token has to appear`() {
        assertTrue(matches("лем соляріс", solaris))
        assertFalse(matches("лем небула", solaris))
    }

    @Test
    fun `token order does not matter`() {
        val byLastNameFirst = book(title = "Соляріс", author = "Лем Станіслав")

        assertTrue(matches("станіслав лем", byLastNameFirst))
        assertTrue(matches("лем станіслав", solaris))
    }

    @Test
    fun `a token may come from the title and another from the author`() {
        assertTrue(matches("соляріс станіслав", solaris))
    }

    @Test
    fun `extra whitespace is not a token`() {
        assertEquals(listOf("лем"), bookSearchTokens("   лем\t\n"))
        assertTrue(matches("  лем   соляріс  ", solaris))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(matches("", solaris))
        assertTrue(matches("   ", solaris))
    }

    /** Deliberate: a blurb hit is a different kind of answer. See the rule. */
    @Test
    fun `the description is not searched`() {
        assertFalse(matches("океаном", solaris))
    }

    /**
     * An unknown author is a placeholder label, not the name of anybody's book,
     * so it is not searchable text — and it must not swallow a query either.
     */
    @Test
    fun `an unknown author matches nothing`() {
        val anonymous = book(title = "Соляріс")

        assertFalse(matches("unknown", anonymous))
        assertFalse(matches("лем", anonymous))
        assertTrue(matches("соляріс", anonymous))
    }
}
