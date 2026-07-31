/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * Inline styling tags of the HTML/EPUB path. `<i>` used to be ignored — the
 * parser only knew `<em>` and FB2's `<emphasis>` — so an EPUB converted from
 * print lost nearly all of its italics.
 */
@RunWith(AndroidJUnit4::class)
class HtmlInlineTagsTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    @Test
    fun iIsItalicised() {
        // The shape a print-converted EPUB uses: <i> around a single stressed
        // word, with the trailing space kept inside the tag
        val paragraph = paragraphs(
            """<p>Він мовив: "це <i>твоя </i>справа", з наголосом на другому слові.</p>"""
        ).single()

        assertEquals(
            """Він мовив: "це твоя справа", з наголосом на другому слові.""",
            paragraph.line.text
        )
        assertItalic(paragraph, "твоя ")
    }

    @Test
    fun emIsStillItalicised() {
        val paragraph = paragraphs("<p>Це <em>значно</em>-краще рішення.</p>").single()

        assertEquals("Це значно-краще рішення.", paragraph.line.text)
        assertItalic(paragraph, "значно")
    }

    @Test
    fun italicWorksInsideAWord() {
        // No CommonMark flanking rules stand in the way of a sentinel
        val paragraph = paragraphs("<p>б<i>о</i>льшинство</p>").single()

        assertEquals("большинство", paragraph.line.text)
        assertItalic(paragraph, "о")
    }

    @Test
    fun htmlStrikethroughTagsAreStruckOut() {
        listOf("s", "del", "strike").forEach { tag ->
            val paragraph = paragraphs("<p>Було <$tag>сто</$tag> двісті.</p>").single()

            assertEquals("<$tag> text", "Було сто двісті.", paragraph.line.text)
            assertTrue(
                "<$tag> is struck through",
                paragraph.line.spanStyles.any { span ->
                    span.item.textDecoration == TextDecoration.LineThrough &&
                            paragraph.line.text.substring(span.start, span.end) == "сто"
                }
            )
        }
    }

    @Test
    fun italicAndBoldCompose() {
        val paragraph = paragraphs("<p>Це <b><i>обидва</i></b> разом.</p>").single()

        assertEquals("Це обидва разом.", paragraph.line.text)
        assertItalic(paragraph, "обидва")
        assertTrue(
            "bold span",
            paragraph.line.spanStyles.any { span ->
                span.item.fontWeight == FontWeight.Medium &&
                        paragraph.line.text.substring(span.start, span.end) == "обидва"
            }
        )
    }

    // --- helpers ---

    private fun assertItalic(paragraph: ReaderText.Text, run: String) {
        assertTrue(
            "italic span over \"$run\"",
            paragraph.line.spanStyles.any { span ->
                span.item.fontStyle == FontStyle.Italic &&
                        paragraph.line.text.substring(span.start, span.end) == run
            }
        )
    }

    /** The first line becomes the chapter title, so the body starts at the second. */
    private fun paragraphs(body: String): List<ReaderText.Text> = runBlocking {
        documentParser
            .parseDocument(Jsoup.parse("<html><body><p>Розділ</p>$body</body></html>"))
            .filterIsInstance<ReaderText.Text>()
    }
}
