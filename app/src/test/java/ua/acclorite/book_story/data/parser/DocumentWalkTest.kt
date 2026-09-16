/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * The DOM walk (#29) — the behaviour the flattening pipeline could not have:
 * block tags end a line (#34), a raw line break inside a paragraph is a space,
 * styles nest instead of toggling, and text is never read as markdown.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentWalkTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    // --- lines ---

    @Test
    fun blockTagsGluedInTheSourceStillEndALine() {
        assertEquals(
            listOf(
                "Heading", "Paragraph.", "Div one.", "Div two.", "Item one", "Item two",
                "A quote.", "After the quote."
            ),
            html(
                "<h3>Heading</h3><p>Paragraph.</p><div>Div one.</div><div>Div two.</div>" +
                        "<ul><li>Item one</li><li>Item two</li></ul>" +
                        "<blockquote>A quote.</blockquote><p>After the quote.</p>"
            ).lines()
        )
    }

    @Test
    fun aHeadingGluedToTheFirstParagraphIsTheWholeChapterTitle() {
        val text = html("<h1>1. Title</h1><p>The first paragraph.</p>", title = false)

        assertEquals("1. Title", text.filterIsInstance<ReaderText.Chapter>().single().title)
        assertEquals(listOf("The first paragraph."), text.lines())
    }

    @Test
    fun aBrStartsANewLine() {
        assertEquals(listOf("one", "two", "three"), html("<p>one<br>two<br/>three</p>").lines())
    }

    @Test
    fun aLineBreakInTheSourceIsASpace() {
        // The hard-wrapped .html book: every source line used to become an entry
        assertEquals(
            listOf("A paragraph wrapped over three lines.", "A div wrapped too."),
            html("<p>A paragraph\n   wrapped over\r\nthree lines.</p><div>A div\nwrapped too.</div>")
                .lines()
        )
    }

    @Test
    fun spacesWithoutALineBreakAreKeptAsTyped() {
        assertEquals(listOf("a  b c"), html("<p>a  b\tc</p>").lines())
    }

    @Test
    fun preKeepsItsLinesAndIndentation() {
        assertEquals(
            listOf("fun main() {", "    println()", "}"),
            html("<pre>fun main() {\n    println()\n}</pre>").lines()
        )
    }

    @Test
    fun anUnclosedHrOrImgDoesNotSwallowTheTextAfterIt() {
        // Read as XML, as an EPUB is: the void element holds what follows it
        val text = xhtml("<p>before<hr>after</p>")

        assertEquals(listOf("before", "after"), text.lines())
        assertTrue(text.any { it is ReaderText.Separator })
    }

    // --- styles ---

    @Test
    fun aStyleInsideTheSameStyleDoesNotCancelIt() {
        val line = html("<p><b>all <strong>of it</strong> bold</b></p>")
            .filterIsInstance<ReaderText.Text>().single().line

        assertEquals("all of it bold", line.text)
        assertTrue(
            "every character is bold",
            line.indices.all { index ->
                line.spanStyles.any { span ->
                    span.item.fontWeight == FontWeight.Medium && index in span.start until span.end
                }
            }
        )
    }

    @Test
    fun aStyleWrappingTwoParagraphsStylesBoth() {
        val lines = xhtml("<em><p>one</p><p>two</p></em>")
            .filterIsInstance<ReaderText.Text>()

        assertEquals(listOf("one", "two"), lines.map { it.line.text })
        lines.forEach { paragraph ->
            assertTrue(
                "\"${paragraph.line.text}\" is italic",
                paragraph.line.spanStyles.any { span ->
                    span.item.fontStyle == FontStyle.Italic &&
                            span.start == 0 && span.end == paragraph.line.length
                }
            )
        }
    }

    @Test
    fun allSixHeadingsAreBold() {
        html((1..6).joinToString("") { level -> "<h$level>h$level</h$level>" })
            .filterIsInstance<ReaderText.Text>()
            .forEach { heading ->
                assertTrue(
                    "${heading.line.text} is bold",
                    heading.line.spanStyles.any { it.item.fontWeight == FontWeight.Medium }
                )
            }
    }

    // --- the book's text is text ---

    @Test
    fun aPipeTableTypedInAParagraphStaysText() {
        // Only a .md book reads pipe tables
        val text = xhtml("<p>| a | b |</p><p>| --- | --- |</p><p>| 1 | 2 |</p>")

        assertTrue("no table", text.none { it is ReaderText.Table })
        assertEquals(listOf("| a | b |", "| --- | --- |", "| 1 | 2 |"), text.lines())
    }

    @Test
    fun markdownInATableCellStaysText() {
        val table = html("<table><tr><td>**not bold**</td></tr></table>")
            .filterIsInstance<ReaderText.Table>().single()

        assertEquals("**not bold**", table.rows.single().single().text)
        assertTrue(table.rows.single().single().spanStyles.isEmpty())
    }

    // --- structure ---

    @Test
    fun aDeeplyNestedDocumentDoesNotOverflowTheStack() {
        // A malformed HTML book can leave thousands of inline tags unclosed
        val depth = 20_000
        val text = xhtml("<p>" + "<span>".repeat(depth) + "deep" + "</span>".repeat(depth) + "</p>")

        assertEquals(listOf("deep"), text.lines())
    }

    @Test
    fun anEmptyLineInANoteIsAGapNotText() = runBlocking {
        val section = Jsoup.parse(
            """<section id="n1"><title><p>1</p></title><p>One.</p><empty-line/><p>Two.</p></section>""",
            "",
            Parser.xmlParser()
        ).selectFirst("section")!!

        assertEquals("One.\n\nTwo.", documentParser.parseNote(section).text)
    }

    // --- helpers ---

    private fun List<ReaderText>.lines(): List<String> =
        filterIsInstance<ReaderText.Text>().map { it.line.text }

    /** An HTML document; unless [title] is false, a first line takes the chapter title. */
    private fun html(body: String, title: Boolean = true): List<ReaderText> = runBlocking {
        val prefix = if (title) "<p>Chapter</p>" else ""
        documentParser.parseDocument(
            Jsoup.parse("<html><body>$prefix$body</body></html>", "", Parser.htmlParser())
        )
    }

    /** An EPUB content document: read as XML, its chapter comes from the TOC. */
    private fun xhtml(body: String): List<ReaderText> = runBlocking {
        documentParser.parseDocument(
            Jsoup.parse("<html><body>$body</body></html>", "", Parser.xmlParser()),
            includeChapter = false
        )
    }
}
