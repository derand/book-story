/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.TableAlignment

/**
 * Per-column table alignment reaches the model from both sources: the markdown
 * delimiter row and the `align`/`text-align` of an FB2/HTML cell.
 */
@RunWith(AndroidJUnit4::class)
class TableAlignmentParsingTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    // --- markdown ---

    @Test
    fun markdownDelimiterColonsBecomeAlignments() {
        val table = markdownTable(
            "| Ліво | Центр | Право | Мовчить |",
            "| :--- | :---: | ----: | ------- |",
            "| a | b | c | d |"
        )

        assertEquals(
            listOf(
                TableAlignment.Start,
                TableAlignment.Center,
                TableAlignment.End,
                TableAlignment.Unspecified
            ),
            table.alignments
        )
    }

    @Test
    fun shortestPossibleDelimitersStillCarryAlignment() {
        val table = markdownTable(
            "| A | B | C |",
            "| :-: | -: | :- |",
            "| a | b | c |"
        )

        assertEquals(
            listOf(TableAlignment.Center, TableAlignment.End, TableAlignment.Start),
            table.alignments
        )
    }

    @Test
    fun aTableWithoutOuterPipesIsReadTheSameWay() {
        val table = markdownTable(
            "Ім'я | Вік",
            "--- | ---:",
            "Аліса | 30"
        )

        assertEquals(
            listOf(TableAlignment.Unspecified, TableAlignment.End),
            table.alignments
        )
    }

    @Test
    fun aPlainDelimiterRowStatesNothing() {
        val table = markdownTable(
            "| A | B |",
            "| --- | --- |",
            "| a | b |"
        )

        assertEquals(
            listOf(TableAlignment.Unspecified, TableAlignment.Unspecified),
            table.alignments
        )
        // The rows themselves must be untouched by the delimiter handling.
        assertEquals(2, table.rows.size)
        assertEquals("A", table.rows[0][0].text)
        assertEquals("a", table.rows[1][0].text)
    }

    // --- FB2 / HTML ---

    @Test
    fun theAlignAttributeOfAnFb2CellIsRead() {
        val table = htmlTable(
            """
            <table>
              <tr><th align="left">Тег</th><th align="center">Статус</th>
                  <th align="right">К-сть</th><th>Нотатка</th></tr>
              <tr><td>emphasis</td><td>курсив</td><td>12</td><td>текст</td></tr>
            </table>
            """
        )

        assertEquals(
            listOf(
                TableAlignment.Start,
                TableAlignment.Center,
                TableAlignment.End,
                TableAlignment.Unspecified
            ),
            table.alignments
        )
    }

    @Test
    fun anInlineTextAlignIsReadWhenThereIsNoAlignAttribute() {
        val table = htmlTable(
            """
            <table>
              <tr><td style="text-align: right; color: red">1</td>
                  <td style="text-align:center">b</td></tr>
            </table>
            """
        )

        assertEquals(
            listOf(TableAlignment.End, TableAlignment.Center),
            table.alignments
        )
    }

    @Test
    fun aColumnTakesTheAlignmentOfTheFirstCellThatStatesOne() {
        // The header says nothing, a body cell does — and a later, disagreeing
        // cell does not overwrite it.
        val table = htmlTable(
            """
            <table>
              <tr><th>Код</th></tr>
              <tr><td align="right">AB-1</td></tr>
              <tr><td align="left">CD-2</td></tr>
            </table>
            """
        )

        assertEquals(listOf(TableAlignment.End), table.alignments)
    }

    @Test
    fun anUnknownAlignmentIsIgnored() {
        val table = htmlTable(
            """<table><tr><td align="justify">a</td><td align="">b</td></tr></table>"""
        )

        assertEquals(
            listOf(TableAlignment.Unspecified, TableAlignment.Unspecified),
            table.alignments
        )
    }

    @Test
    fun aTableThatStatesNothingKeepsAnEmptyAlignmentPerColumn() {
        val table = htmlTable(
            """<table><tr><th>A</th><th>B</th></tr><tr><td>a</td><td>b</td></tr></table>"""
        )

        assertEquals(
            listOf(TableAlignment.Unspecified, TableAlignment.Unspecified),
            table.alignments
        )
        assertEquals(TableAlignment.Unspecified, table.alignmentAt(0))
        // Reading past the last column must not throw.
        assertEquals(TableAlignment.Unspecified, table.alignmentAt(9))
    }

    // --- helpers ---

    /** A markdown table reaches the parser as plain text lines, not as a DOM. */
    private fun markdownTable(vararg lines: String): ReaderText.Table =
        parse(lines.joinToString("\n"))

    private fun htmlTable(html: String): ReaderText.Table = parse(html)

    /**
     * `parseDocument` drops a document that has no chapter or no paragraph at
     * all, so the table always travels with a line of text on either side —
     * the first becomes the chapter, the second a paragraph.
     */
    private fun parse(body: String): ReaderText.Table = runBlocking {
        val document = Jsoup.parse(
            "<FictionBook><body>\nЗаголовок тесту.\n\n$body\n\nТекст після.\n</body></FictionBook>",
            "",
            Parser.xmlParser()
        )
        documentParser.parseDocument(document)
            .filterIsInstance<ReaderText.Table>()
            .single()
    }
}
