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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.TableAlignment

class ParsedTextCodecTest {

    @Test
    fun empty() = roundTrip(ParsedText.EMPTY)

    @Test
    fun allElementTypes() {
        val linkedText = buildAnnotatedString {
            append("see ")
            withLink(
                LinkAnnotation.Url(
                    "https://example.com",
                    styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                )
            ) {
                // bold inside a link — exercises overlapping annotations
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("bold link") }
            }
        }

        val parsed = ParsedText(
            text = listOf(
                ReaderText.Chapter(title = "Розділ 1", depth = 0),
                ReaderText.Chapter(title = "Nested", depth = 2),
                // A title with inline markup: italic plus a footnote reference,
                // which the plain title drops
                ReaderText.Chapter(
                    title = "Styled",
                    depth = 1,
                    styledTitle = buildAnnotatedString {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Styled") }
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "note:n1",
                                styles = TextLinkStyles(
                                    SpanStyle(
                                        baselineShift = BaselineShift.Superscript,
                                        fontSize = 0.75.em
                                    )
                                ),
                                linkInteractionListener = null
                            )
                        ) { append("[1]") }
                    }
                ),
                ReaderText.Text(
                    line = buildAnnotatedString {
                        append("A ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("bold") }
                        append(" paragraph.")
                    },
                    role = ReaderTextRole.Paragraph
                ),
                ReaderText.Text(line = linkedText, role = ReaderTextRole.Title),
                ReaderText.Separator,
                ReaderText.Poem(
                    lines = listOf(
                        ReaderText.Text(AnnotatedString("First verse")),
                        ReaderText.Text(
                            line = buildAnnotatedString {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Author") }
                            },
                            role = ReaderTextRole.TextAuthor
                        )
                    )
                ),
                ReaderText.Table(
                    rows = listOf(
                        listOf(
                            AnnotatedString("H1"),
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("H2") }
                            }
                        ),
                        listOf(AnnotatedString("a"), AnnotatedString("b"))
                    ),
                    hasHeader = true,
                    alignments = listOf(TableAlignment.End, TableAlignment.Center)
                ),
                // A table from a source that states no alignment at all.
                ReaderText.Table(
                    rows = listOf(listOf(AnnotatedString("x"), AnnotatedString("y"))),
                    hasHeader = false
                ),
                ReaderText.Image(
                    image = ReaderImage(
                        id = "cover.jpg-3-42",
                        src = "cover.jpg",
                        bytes = byteArrayOf(1, 2, 3),
                        width = 100,
                        height = 200
                    ),
                    caption = ReaderText.Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("A caption") }
                        }
                    )
                ),
                ReaderText.Image(
                    image = ReaderImage(
                        id = "pic.png-1-7",
                        src = "pic.png",
                        bytes = byteArrayOf(9),
                        width = 10,
                        height = 20
                    ),
                    caption = null
                )
            ),
            notes = mapOf(
                "n1" to AnnotatedString("A plain note."),
                "n2" to buildAnnotatedString {
                    append("H")
                    withStyle(
                        SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
                    ) { append("2") }
                    append("O note")
                }
            )
        )

        roundTrip(parsed)
    }

    // --- helpers: compare the render-relevant contract, not order-sensitive equals ---

    private fun roundTrip(parsed: ParsedText) {
        val restored = ParsedTextCodec.decodeFromBytes(ParsedTextCodec.encodeToBytes(parsed))
        assertParsedTextEquals(parsed, restored)
    }

    private fun assertParsedTextEquals(expected: ParsedText, actual: ParsedText) {
        assertEquals("text size", expected.text.size, actual.text.size)
        expected.text.forEachIndexed { i, element ->
            assertElementEquals("text[$i]", element, actual.text[i])
        }
        assertEquals("note keys", expected.notes.keys, actual.notes.keys)
        expected.notes.forEach { (key, value) ->
            assertAnnotatedEquals("note[$key]", value, actual.notes.getValue(key))
        }
    }

    private fun assertElementEquals(msg: String, expected: ReaderText, actual: ReaderText) {
        assertEquals("$msg type", expected::class, actual::class)
        when (expected) {
            is ReaderText.Chapter -> {
                actual as ReaderText.Chapter
                assertEquals("$msg id", expected.id, actual.id)
                assertEquals("$msg title", expected.title, actual.title)
                assertEquals("$msg depth", expected.depth, actual.depth)
                val expectedStyled = expected.styledTitle
                if (expectedStyled == null) {
                    assertEquals("$msg styledTitle", null, actual.styledTitle)
                } else {
                    assertAnnotatedEquals("$msg styledTitle", expectedStyled, actual.styledTitle!!)
                }
            }

            is ReaderText.Text -> assertTextEquals(msg, expected, actual as ReaderText.Text)

            is ReaderText.Poem -> {
                actual as ReaderText.Poem
                assertEquals("$msg poem size", expected.lines.size, actual.lines.size)
                expected.lines.forEachIndexed { i, line ->
                    assertTextEquals("$msg line[$i]", line, actual.lines[i])
                }
            }

            is ReaderText.Table -> {
                actual as ReaderText.Table
                assertEquals("$msg hasHeader", expected.hasHeader, actual.hasHeader)
                assertEquals("$msg alignments", expected.alignments, actual.alignments)
                assertEquals("$msg rows", expected.rows.size, actual.rows.size)
                expected.rows.forEachIndexed { r, row ->
                    assertEquals("$msg row[$r] size", row.size, actual.rows[r].size)
                    row.forEachIndexed { c, cell ->
                        assertAnnotatedEquals("$msg cell[$r,$c]", cell, actual.rows[r][c])
                    }
                }
            }

            is ReaderText.Separator -> Unit

            is ReaderText.Image -> {
                actual as ReaderText.Image
                assertEquals("$msg image id", expected.image.id, actual.image.id)
                assertEquals("$msg image src", expected.image.src, actual.image.src)
                assertEquals("$msg image width", expected.image.width, actual.image.width)
                assertEquals("$msg image height", expected.image.height, actual.image.height)
                val expectedCaption = expected.caption
                if (expectedCaption == null) {
                    assertEquals("$msg caption", null, actual.caption)
                } else {
                    assertTextEquals("$msg caption", expectedCaption, actual.caption!!)
                }
            }
        }
    }

    private fun assertTextEquals(msg: String, expected: ReaderText.Text, actual: ReaderText.Text) {
        assertEquals("$msg role", expected.role, actual.role)
        assertAnnotatedEquals(msg, expected.line, actual.line)
    }

    private fun assertAnnotatedEquals(msg: String, expected: AnnotatedString, actual: AnnotatedString) {
        assertEquals("$msg text", expected.text, actual.text)
        assertEquals("$msg spanStyles", expected.spanStyles, actual.spanStyles)
        assertEquals(
            "$msg links",
            expected.getLinkAnnotations(0, expected.length),
            actual.getLinkAnnotations(0, actual.length)
        )
    }
}
