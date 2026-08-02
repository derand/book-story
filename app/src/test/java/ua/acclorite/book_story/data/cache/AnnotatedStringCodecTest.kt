/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trips every span/link shape the parser emits through
 * [AnnotatedStringCodec] and asserts the result equals the original. The styles
 * below mirror [ua.acclorite.book_story.data.parser.document.MarkdownParser].
 */
class AnnotatedStringCodecTest {

    private fun roundTrip(value: AnnotatedString) {
        val restored = AnnotatedStringCodec.decodeFromBytes(
            AnnotatedStringCodec.encodeToBytes(value)
        )
        // Compare what the cache must preserve — the rendered result: text, span
        // styles and link annotations. Not AnnotatedString.equals(), which is
        // sensitive to the insertion order of overlapping annotations (an internal
        // detail that does not affect rendering and is not part of the contract).
        assertEquals("text", value.text, restored.text)
        assertEquals("spanStyles", value.spanStyles, restored.spanStyles)
        assertEquals(
            "links",
            value.getLinkAnnotations(0, value.length),
            restored.getLinkAnnotations(0, restored.length)
        )
    }

    @Test
    fun plainText() = roundTrip(AnnotatedString("Just plain text, кирилиця, 123."))

    @Test
    fun emptyText() = roundTrip(AnnotatedString(""))

    @Test
    fun bold() = roundTrip(
        buildAnnotatedString {
            append("a ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("bold") }
            append(" b")
        }
    )

    @Test
    fun italic() = roundTrip(
        buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("italic") }
        }
    )

    @Test
    fun monospace() = roundTrip(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append("code()") }
        }
    )

    @Test
    fun strikethrough() = roundTrip(
        buildAnnotatedString {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("gone") }
        }
    )

    @Test
    fun subscript() = roundTrip(
        buildAnnotatedString {
            append("H")
            withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)) {
                append("2")
            }
            append("O")
        }
    )

    @Test
    fun superscript() = roundTrip(
        buildAnnotatedString {
            append("x")
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)) {
                append("2")
            }
        }
    )

    @Test
    fun boldItalicComposed() = roundTrip(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append("bold ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("and italic") }
            }
        }
    )

    @Test
    fun externalLink() = roundTrip(
        buildAnnotatedString {
            append("see ")
            withLink(
                LinkAnnotation.Url(
                    "https://example.com",
                    styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                )
            ) { append("here") }
        }
    )

    @Test
    fun noteReference() = roundTrip(
        buildAnnotatedString {
            append("text")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "note:6162",
                    styles = TextLinkStyles(
                        SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
                    ),
                    linkInteractionListener = null
                )
            ) { append("[1]") }
        }
    )

    @Test
    fun anchorReference() = roundTrip(
        buildAnnotatedString {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "anchor:6162",
                    styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = null
                )
            ) { append("jump") }
            append(" back")
        }
    )

    @Test
    fun styledTextInsideLink() = roundTrip(
        buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    "https://example.com",
                    styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                )
            ) {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("bold link") }
            }
        }
    )

    @Test
    fun everythingCombined() = roundTrip(
        buildAnnotatedString {
            append("intro ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("bold ") }
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("italic ") }
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append("mono ") }
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("struck ") }
            append("water H")
            withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)) {
                append("2")
            }
            append("O and note")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "note:99",
                    styles = TextLinkStyles(
                        SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
                    ),
                    linkInteractionListener = null
                )
            ) { append("[2]") }
            append(" end")
        }
    )
}
