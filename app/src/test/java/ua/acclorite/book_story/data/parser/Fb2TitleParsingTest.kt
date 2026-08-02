/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.text.markChapterTitles
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole

/**
 * FB2 <title> handling: a chapter title keeps its inline markup (and its
 * footnote references), while the chapter list keeps a plain title.
 */
@RunWith(RobolectricTestRunner::class)
class Fb2TitleParsingTest {

    // The default builder enables every block type, while the app narrows them
    // (see AppModule): a stricter parser than production, on purpose.
    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    @Test
    fun plainTitleCarriesNoStyling() {
        val chapter = chapters("<title><p>Просто заголовок</p></title>").single()

        assertEquals("Просто заголовок", chapter.title)
        assertNull("a plain title needs no styled copy", chapter.styledTitle)
    }

    @Test
    fun inlineMarkupIsKept() {
        val chapter = chapters(
            "<title><p>15. <emphasis>курсив</emphasis>, <strong>жирний</strong>, " +
                    "H<sub>2</sub>O, mc<sup>2</sup></p></title>"
        ).single()

        // Plain title: no markup characters leak into the chapter list
        assertEquals("15. курсив, жирний, H2O, mc2", chapter.title)

        assertNotNull("styled title", chapter.styledTitle)
        val styled = chapter.styledTitle!!
        assertEquals(chapter.title, styled.text)
        assertTrue(
            "italic span",
            styled.spanStyles.any { span ->
                span.item.fontStyle == FontStyle.Italic &&
                        styled.text.substring(span.start, span.end) == "курсив"
            }
        )
        assertTrue(
            "bold span",
            styled.spanStyles.any { span ->
                span.item.fontWeight == FontWeight.Medium &&
                        styled.text.substring(span.start, span.end) == "жирний"
            }
        )
        assertTrue(
            "subscript span",
            styled.spanStyles.any { span ->
                span.item.baselineShift == BaselineShift.Subscript &&
                        styled.text.substring(span.start, span.end) == "2"
            }
        )
        assertTrue(
            "superscript span",
            styled.spanStyles.any { span ->
                span.item.baselineShift == BaselineShift.Superscript &&
                        styled.text.substring(span.start, span.end) == "2"
            }
        )
    }

    @Test
    fun footnoteReferenceIsTappableAndOutOfThePlainTitle() {
        val chapter = chapters(
            """<title><p>16. Заголовок<a l:href="#note7" type="note">[7]</a></p></title>"""
        ).single()

        // The chapter list has no use for a dangling marker
        assertEquals("16. Заголовок", chapter.title)

        val styled = chapter.styledTitle!!
        assertEquals("16. Заголовок[7]", styled.text)
        val link = styled.getLinkAnnotations(0, styled.length).single()
        assertEquals("[7]", styled.text.substring(link.start, link.end))
        assertEquals("note:note7", (link.item as LinkAnnotation.Clickable).tag)
    }

    @Test
    fun leadingListMarkerSurvives() {
        // Commonmark would read these as list/quote/heading markers and drop them
        listOf(
            "1. Пролог" to "1. Пролог",
            "17) Розділ" to "17) Розділ",
            "- Пролог" to "- Пролог",
            "# Пролог" to "# Пролог",
            "11.1. Вкладений" to "11.1. Вкладений"
        ).forEach { (raw, expected) ->
            assertEquals(expected, chapters("<title><p>$raw</p></title>").single().title)
        }
    }

    @Test
    fun multiParagraphTitleBecomesOneLine() {
        val chapter = chapters(
            "<title>\n  <p>18. Заголовок</p>\n  <p>другий рядок</p>\n</title>"
        ).single()

        assertEquals("18. Заголовок другий рядок", chapter.title)
    }

    @Test
    fun poemTitleKeepsItsMarkup() {
        val text = parse(
            """
            <section>
              <title><p>Розділ</p></title>
              <p>Текст секції.</p>
              <poem>
                <title><p>Назва з <emphasis>курсивом</emphasis></p></title>
                <stanza><v>Рядок</v></stanza>
              </poem>
            </section>
            """.trimIndent()
        )

        val title = text.filterIsInstance<ReaderText.Poem>().single()
            .lines.first { line -> line.role == ReaderTextRole.Title }

        assertEquals("Назва з курсивом", title.line.text)
        // The bold run is split at every inline mark, so cover, not span, is
        // what matters: every character of the title has to be bold.
        val bold = title.line.spanStyles.filter { span ->
            span.item.fontWeight == FontWeight.Medium
        }
        assertTrue(
            "the whole title is bold",
            title.line.indices.all { index ->
                bold.any { span -> index >= span.start && index < span.end }
            }
        )
        assertTrue(
            "italic is kept inside",
            title.line.spanStyles.any { span ->
                span.item.fontStyle == FontStyle.Italic &&
                        title.line.text.substring(span.start, span.end) == "курсивом"
            }
        )
    }

    // --- helpers ---

    private fun chapters(title: String): List<ReaderText.Chapter> =
        parse("<section>$title<p>Текст секції.</p></section>")
            .filterIsInstance<ReaderText.Chapter>()

    /** Runs the FB2 body through the same two steps as [XmlTextParser]. */
    private fun parse(body: String): List<ReaderText> = runBlocking {
        val document = Jsoup.parse(
            """<FictionBook><body>$body</body></FictionBook>""",
            "",
            Parser.xmlParser()
        )
        document.markChapterTitles()
        documentParser.parseDocument(document)
    }
}
