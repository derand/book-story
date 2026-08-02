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
import org.junit.Assert.assertThrows
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.TableAlignment
import java.util.UUID

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

    // --- the enum codes on the wire ---
    //
    // A round trip cannot defend these: it encodes and decodes with the same
    // enum in the same process, so `Title` -> 1 -> `Title` holds however the
    // members are ordered. The damage only appears across a version boundary —
    // bytes written by yesterday's build, read by today's — which is why the
    // format is pinned below rather than merely round-tripped.

    @Test
    fun everyRoleRoundTrips() {
        // Iterates `entries`, so a role added later is covered without an edit.
        ReaderTextRole.entries.forEach { role ->
            val parsed = ParsedText(
                text = listOf(ReaderText.Text(AnnotatedString("x"), role)),
                notes = emptyMap()
            )
            val restored = ParsedTextCodec.decodeFromBytes(ParsedTextCodec.encodeToBytes(parsed))
            assertEquals(role, (restored.text.single() as ReaderText.Text).role)
        }
    }

    @Test
    fun everyAlignmentRoundTrips() {
        val parsed = ParsedText(
            text = listOf(
                ReaderText.Table(
                    rows = emptyList(),
                    hasHeader = false,
                    alignments = TableAlignment.entries
                )
            ),
            notes = emptyMap()
        )
        val restored = ParsedTextCodec.decodeFromBytes(ParsedTextCodec.encodeToBytes(parsed))
        assertEquals(TableAlignment.entries, (restored.text.single() as ReaderText.Table).alignments)
    }

    /**
     * The whole wire format, frozen. Guards the element type bytes, the field
     * order and — the reason this test exists — the numeric code of every role
     * and alignment, none of which any round trip can see.
     *
     * **If this fails and you changed the format on purpose:** bump
     * `ParsedTextCodec.VERSION` *and* `ParseCache.VERSION` so stale entries are
     * ignored rather than misread, then paste the new bytes in below.
     */
    @Test
    fun theWireFormatIsFrozen() {
        val expected = (
            "000000030000000a006cca978a00004000800000000000000100016300000001" +
                "0100000001730000000000000000010000000001700000000000000000010100" +
                "0000017400000000000000000102000000016500000000000000000103000000" +
                "0161000000000000000004020000000100000000017600000000000000000301" +
                "0000000100000002000000013100000000000000000000000132000000000000" +
                "00000000000400010203050001690005692e706e670000000300000002010000" +
                "0000016b00000000000000000500016a00056a2e706e67000000010000000100" +
                "0000000100016e000000046e6f74650000000000000000"
            )

        val actual = ParsedTextCodec.encodeToBytes(wireFixture())
            .joinToString("") { byte -> "%02x".format(byte) }

        assertEquals("wire format changed — see this test's doc comment", expected, actual)
        // And the bytes still mean what they say.
        assertParsedTextEquals(wireFixture(), ParsedTextCodec.decodeFromBytes(actual.hexToBytes()))
    }

    @Test
    fun anUnknownRoleCodeIsRejected() {
        // VERSION (4 bytes) + element count (4) + the TYPE_TEXT tag (1).
        val bytes = ParsedTextCodec.encodeToBytes(
            ParsedText(
                text = listOf(ReaderText.Text(AnnotatedString("x"), ReaderTextRole.Title)),
                notes = emptyMap()
            )
        )
        assertEquals("role byte offset", 1, bytes[9].toInt())

        bytes[9] = 99
        // Must throw rather than return something plausible: ParseCache.read
        // turns an exception into a clean miss, and a wrong role into a book
        // that renders wrong.
        assertThrows(IllegalArgumentException::class.java) {
            ParsedTextCodec.decodeFromBytes(bytes)
        }
    }

    @Test
    fun anUnknownAlignmentCodeIsRejected() {
        // ... + hasHeader (1) + row count (4) + alignment count (4).
        val bytes = ParsedTextCodec.encodeToBytes(
            ParsedText(
                text = listOf(
                    ReaderText.Table(
                        rows = emptyList(),
                        hasHeader = false,
                        alignments = listOf(TableAlignment.Start)
                    )
                ),
                notes = emptyMap()
            )
        )
        assertEquals("alignment byte offset", 1, bytes[18].toInt())

        bytes[18] = 99
        assertThrows(IllegalArgumentException::class.java) {
            ParsedTextCodec.decodeFromBytes(bytes)
        }
    }

    /**
     * Every element type, role and alignment exactly once, and nothing that
     * could drift: plain [AnnotatedString]s, a fixed chapter id.
     *
     * Members are listed by hand rather than read from `entries` on purpose —
     * **appending** an enum member is backwards compatible and must not disturb
     * the frozen bytes. The two round-trip tests above cover a new member.
     */
    private fun wireFixture() = ParsedText(
        text = listOf(
            ReaderText.Chapter(
                id = UUID.fromString("6cca978a-0000-4000-8000-000000000001"),
                title = "c",
                depth = 1,
                styledTitle = AnnotatedString("s")
            ),
            ReaderText.Text(AnnotatedString("p"), ReaderTextRole.Paragraph),
            ReaderText.Text(AnnotatedString("t"), ReaderTextRole.Title),
            ReaderText.Text(AnnotatedString("e"), ReaderTextRole.Epigraph),
            ReaderText.Text(AnnotatedString("a"), ReaderTextRole.TextAuthor),
            ReaderText.Separator,
            ReaderText.Poem(lines = listOf(ReaderText.Text(AnnotatedString("v")))),
            ReaderText.Table(
                rows = listOf(listOf(AnnotatedString("1"), AnnotatedString("2"))),
                hasHeader = true,
                alignments = listOf(
                    TableAlignment.Unspecified,
                    TableAlignment.Start,
                    TableAlignment.Center,
                    TableAlignment.End
                )
            ),
            ReaderText.Image(
                image = ReaderImage(id = "i", src = "i.png", bytes = byteArrayOf(7), width = 3, height = 2),
                caption = ReaderText.Text(AnnotatedString("k"))
            ),
            ReaderText.Image(
                image = ReaderImage(id = "j", src = "j.png", bytes = ByteArray(0), width = 1, height = 1),
                caption = null
            )
        ),
        notes = mapOf("n" to AnnotatedString("note"))
    )

    private fun String.hexToBytes() =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }

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
