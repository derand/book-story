/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.dialog

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitedEditTest {

    private fun value(text: String, cursor: Int = text.length) =
        TextFieldValue(text, TextRange(cursor))

    @Test
    fun `text up to the limit is taken as is`() {
        val new = value("a".repeat(10))
        assertEquals(new, limitedEdit(value("a".repeat(9)), new, 10))
    }

    @Test
    fun `a paste into an empty field is cut to the limit, not refused`() {
        val result = limitedEdit(value(""), value("x".repeat(120)), 100)
        assertEquals("x".repeat(100), result.text)
        assertEquals(TextRange(100), result.selection)
    }

    @Test
    fun `a paste in the middle cuts the pasted part and keeps both sides`() {
        val old = value("abcdef", cursor = 3)
        val new = value("abc123456def", cursor = 9)
        val result = limitedEdit(old, new, 8)
        assertEquals("abc12def", result.text)
        assertEquals(TextRange(5), result.selection)
    }

    @Test
    fun `a paste over a selection replaces it and is cut to fit`() {
        val old = TextFieldValue("abcdef", TextRange(1, 5))
        val new = value("a123456f", cursor = 7)
        val result = limitedEdit(old, new, 7)
        assertEquals("a12345f", result.text)
    }

    @Test
    fun `a value already over the limit is not cut, only kept from growing`() {
        val old = value("t".repeat(150))
        val shorter = value("t".repeat(149))
        assertEquals(shorter, limitedEdit(old, shorter, 100))

        val typed = limitedEdit(old, value("t".repeat(150) + "x"), 100)
        assertEquals("t".repeat(150), typed.text)
    }

    @Test
    fun `a cut never splits a surrogate pair`() {
        val result = limitedEdit(value("ab"), value("ab😀"), 3)
        assertEquals("ab", result.text)
    }
}
