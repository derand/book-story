/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.acclorite.book_story.R

/** Two texts that say the same thing are equal, as a `Book`'s author must be. */
class UITextTest {

    @Test
    fun `resources with the same id and arguments are equal`() {
        val a = UIText.StringResource(R.string.unknown_author)
        val b = UIText.StringResource(R.string.unknown_author)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        assertEquals(
            UIText.StringResource(R.string.unknown_author, "x", 1),
            UIText.StringResource(R.string.unknown_author, "x", 1)
        )
    }

    @Test
    fun `a different id or argument makes them differ`() {
        assertNotEquals(
            UIText.StringResource(R.string.unknown_author),
            UIText.StringResource(R.string.unknown_author, "x")
        )
        assertNotEquals(
            UIText.StringResource(R.string.unknown_author, "x"),
            UIText.StringResource(R.string.unknown_author, "y")
        )
        assertNotEquals(
            UIText.StringResource(R.string.unknown_author),
            UIText.StringValue("Unknown")
        )
    }

    @Test
    fun `only a literal has a literal`() {
        assertEquals("Ann Lee", UIText.StringValue("Ann Lee").literalOrNull())
        assertNull(UIText.StringResource(R.string.unknown_author).literalOrNull())
    }
}
