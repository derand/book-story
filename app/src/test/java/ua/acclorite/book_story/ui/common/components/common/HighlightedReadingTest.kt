/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Highlighted ("bionic") reading: which letters, and that nothing else moves. */
class HighlightedReadingTest {

    private val thickness = FontWeight.SemiBold

    /** The highlighted parts of [text], as strings. */
    private fun highlighted(text: String): List<String> =
        highlightedReadingRanges(text).map { text.substring(it) }

    @Test
    fun `half of each word, one letter of a three-letter word`() {
        assertEquals(
            listOf("a", "t", "c", "wo", "fiv"),
            highlighted("a to cat word fives")
        )
    }

    @Test
    fun `non-letters at either end are skipped and not counted`() {
        // «word», — the quote is skipped, the comma is not counted: "wo".
        // 2nd: digits first, then three letters, so one letter.
        assertEquals(listOf("wo", "a"), highlighted("«word», 2abc"))
    }

    @Test
    fun `a word without letters gets nothing`() {
        assertEquals(emptyList<String>(), highlighted("— 12 ... [1]"))
    }

    @Test
    fun `the text and its link offsets stay as given, double spaces included`() {
        val line = buildAnnotatedString {
            append("one  two   three  marker")
            withLink(LinkAnnotation.Clickable("note1", linkInteractionListener = null)) { append("[1]") }
            append("  after")
        }

        val result = line.withHighlightedReading(thickness)

        assertEquals(line.text, result.text)
        assertEquals(
            line.getLinkAnnotations(0, line.length).map { it.start to it.end },
            result.getLinkAnnotations(0, result.length).map { it.start to it.end }
        )
        assertEquals(
            listOf("o", "t", "thr", "mar", "aft"),
            result.spanStyles
                .filter { it.item.fontWeight == thickness }
                .map { result.text.substring(it.start, it.end) }
        )
    }

    @Test
    fun `the text's own weight wins over the highlight`() {
        val line = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("bold") }
            append(" plain")
        }

        val result = line.withHighlightedReading(thickness)

        // Of overlapping spans the later one wins, so the text's own bold
        // has to come after the highlight laid over "bo".
        val overFirstLetter = result.spanStyles
            .filter { it.start == 0 }
            .map { it.item.fontWeight }
        assertEquals(listOf(thickness, FontWeight.Bold), overFirstLetter)
    }

    @Test
    fun `nothing to highlight returns the same string`() {
        val line = AnnotatedString("— 12 —")
        assertSame(line, line.withHighlightedReading(thickness))
    }
}
