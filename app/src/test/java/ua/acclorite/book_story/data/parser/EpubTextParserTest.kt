/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.text.EpubTextParser
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The EPUB path end to end: a real archive through [EpubTextParser], not a
 * fragment handed to [DocumentParser].
 *
 * Two layers. The targeted tests cover what only the EPUB parser does — the
 * spine, the TOC, the chapter title fallback, which documents are dropped. The
 * snapshot pins the whole output of a tag-coverage book, *as it is today*,
 * known defects included: it is the regression net for rewriting the parsing
 * core (#29), and every change in behaviour has to show up as a diff of it.
 */
@RunWith(RobolectricTestRunner::class)
class EpubTextParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val parser = EpubTextParser(
        DocumentParser(MarkdownParser(CommonmarkParser.builder().build()))
    )

    /** A 3x2 PNG. */
    private val png = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAEElEQVR4nGP4" +
                "z8AAQQxwFgBB0gX7h/C5SAAAAABJRU5ErkJggg=="
    )

    // --- the snapshot ---

    @Test
    fun tagCoverageBookMatchesItsSnapshot() {
        val source = resourceFile("epub/tags-test")
        val book = temp.newFile("tags-test.epub")
        ZipOutputStream(book.outputStream()).use { out ->
            source.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(source).invariantSeparatorsPath }
                .forEach { file ->
                    out.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    out.write(file.readBytes())
                    out.closeEntry()
                }
        }

        assertSnapshot("epub/tags-test.snapshot.txt", parse(book).text.dump())
    }

    // --- chapter order ---

    @Test
    fun theSpineDecidesTheOrderNotTheFileNames() {
        val book = epub(
            "OEBPS/content.opf" to opf("b.xhtml", "a.xhtml"),
            "OEBPS/a.xhtml" to xhtml("<p>Перший за іменем</p><p>текст а</p>"),
            "OEBPS/b.xhtml" to xhtml("<p>Перший за spine</p><p>текст б</p>")
        )

        assertEquals(
            listOf("Перший за spine", "Перший за іменем"),
            parse(book).chapterTitles()
        )
    }

    @Test
    fun withoutAnOpfDocumentsAreOrderedByTheDigitsInTheirNames() {
        val book = epub(
            "text/part10.html" to xhtml("<p>Десятий</p><p>текст</p>"),
            "text/part2.xhtml" to xhtml("<p>Другий</p><p>текст</p>"),
            "text/part1.htm" to xhtml("<p>Перший</p><p>текст</p>")
        )

        assertEquals(listOf("Перший", "Другий", "Десятий"), parse(book).chapterTitles())
    }

    // --- chapter titles ---

    @Test
    fun theTocNamesTheChapterAndANestedNavPointIsOneLevelDeep() {
        // Read as HTML, <content src="…"/> was left open and the nested
        // navPoint ended up inside it, so every chapter came out at depth 0
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml", "two.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Частина", "one.xhtml", navPoint("Розділ", "two.xhtml"))
            ),
            "OEBPS/one.xhtml" to xhtml("<p>текст один</p>"),
            "OEBPS/two.xhtml" to xhtml("<p>текст два</p>")
        )

        val chapters = parse(book).text.filterIsInstance<ReaderText.Chapter>()
        assertEquals(listOf("Частина", "Розділ"), chapters.map { it.title })
        assertEquals(listOf(0, 1), chapters.map { it.depth })
    }

    @Test
    fun twoNavPointsOnOneDocumentAreJoined() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Перша назва", "one.xhtml"),
                navPoint("Друга назва", "one.xhtml#middle")
            ),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Перша назва / Друга назва"), parse(book).chapterTitles())
    }

    @Test
    fun aTocSourceIsMatchedDecodedAndWithoutItsFragment() {
        val book = epub(
            "OEBPS/content.opf" to opf("my%20chapter.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("З TOC", "Text/my%20chapter.xhtml#start")),
            "OEBPS/Text/my chapter.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("З TOC"), parse(book).chapterTitles())
    }

    @Test
    fun withoutATocEntryTheFirstLineBecomesTheTitleAndLeavesTheText() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/one.xhtml" to xhtml("<p>Назва з тексту</p><p>Перший абзац.</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Назва з тексту"), text.chapterTitles())
        assertEquals(listOf("Перший абзац."), text.paragraphs())
    }

    @Test
    fun aFirstLineRepeatingTheTocTitleIsDroppedIgnoringCase() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ Перший", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>РОЗДІЛ ПЕРШИЙ</p><p>Перший абзац.</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ Перший"), text.chapterTitles())
        assertEquals(listOf("Перший абзац."), text.paragraphs())
    }

    // --- the document itself ---

    @Test
    fun aSelfClosingTitleKeepsTheWholeText() {
        // Valid XHTML, and found in published books. Read as HTML, <title> is
        // never self-closing, so what followed it was read as the page title;
        // jsoup rewinds out of that only within its read-ahead window, so a
        // document past roughly 2 KB lost the text that went into the title.
        // Hence the size: a short document never showed it.
        val paragraphs = (1..200).map { n -> "Абзац номер $n." }
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to
                    """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<html xmlns="http://www.w3.org/1999/xhtml"><head><title/></head><body>""" +
                    paragraphs.joinToString("") { "<p>$it</p>" } +
                    "</body></html>"
        )

        assertEquals(paragraphs, parse(book).text.paragraphs())
    }

    @Test
    fun aLineBreakInsideAParagraphDoesNotSplitIt() {
        // Both line endings, and the page anchor print editions leave between
        // two lines of one paragraph
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml(
                "<p>перший рядок\nдругий</p>" +
                        "<p>до якоря \r\n\r\n<a id=\"p17\"/>після якоря</p>"
            )
        )

        assertEquals(
            listOf("перший рядок другий", "до якоря після якоря"),
            parse(book).text.paragraphs()
        )
    }

    @Test
    fun scriptAndStyleInTheBodyAreNotText() {
        // HTML keeps what they hold out of the text; XML would make it text
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml(
                "<style>p { margin: 0 }</style><p>Абзац.</p><script>var x = 1;</script>"
            )
        )

        assertEquals(listOf("Абзац."), parse(book).text.paragraphs())
    }

    @Test
    fun notQuiteXmlStillParses() {
        // What real books get wrong: an HTML entity no DTD declares, an
        // unclosed <br>, a tag in upper case. None of it may cost text.
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml(
                "<p>один&nbsp;два</p><p>рядок<br>далі</p><P>великі <I>літери</I></P>"
            )
        )

        val text = parse(book).text.filterIsInstance<ReaderText.Text>()
        assertEquals(
            listOf("один два", "рядок", "далі", "великі літери"),
            text.map { it.line.text }
        )
        assertTrue(
            "upper-case <I> is still italic",
            text.last().line.spanStyles.any { span ->
                span.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic
            }
        )
    }

    // --- which documents are dropped ---

    @Test
    fun aDocumentWithNothingButItsTitleIsDropped() {
        val book = epub(
            "OEBPS/content.opf" to opf("empty.xhtml", "one.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Порожній", "empty.xhtml"),
                navPoint("Повний", "one.xhtml")
            ),
            "OEBPS/empty.xhtml" to xhtml("<div> </div>"),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Повний"), parse(book).chapterTitles())
    }

    @Test
    fun anImageOnlyDocumentWithATocEntryIsKept() {
        val book = epub(
            "OEBPS/content.opf" to opf("cover.xhtml", "one.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Обкладинка", "cover.xhtml"),
                navPoint("Розділ", "one.xhtml")
            ),
            "OEBPS/cover.xhtml" to xhtml("<div><img src=\"../Images/cover.png\"/></div>"),
            "OEBPS/Images/cover.png" to png,
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Обкладинка", "Розділ"), text.chapterTitles())
        val image = text.filterIsInstance<ReaderText.Image>().single()
        assertEquals("cover.png", image.image.src)
        assertEquals(3, image.image.width)
        assertEquals(2, image.image.height)
    }

    @Test
    fun knownEdge_anImageOnlyDocumentWithoutATocEntryIsDropped() {
        // No line to fall back on for a title, and inventing one is worse than
        // having none — kept deliberately since #33
        val book = epub(
            "OEBPS/content.opf" to opf("cover.xhtml", "one.xhtml"),
            "OEBPS/cover.xhtml" to xhtml("<div><img src=\"cover.png\"/></div>"),
            "OEBPS/cover.png" to png,
            "OEBPS/one.xhtml" to xhtml("<p>Розділ</p><p>текст</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ"), text.chapterTitles())
        assertTrue("the cover went with it", text.none { it is ReaderText.Image })
    }

    @Test
    fun aBookWithNoContentAtAllIsEmpty() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/one.xhtml" to xhtml("<p> </p>")
        )

        assertEquals(ParsedText.EMPTY, parse(book))
    }

    // --- helpers ---

    private fun parse(book: File): ParsedText = runBlocking {
        parser.parse(CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), book))
    }

    private fun ParsedText.chapterTitles(): List<String> = text.chapterTitles()

    private fun List<ReaderText>.chapterTitles(): List<String> =
        filterIsInstance<ReaderText.Chapter>().map { it.title }

    private fun List<ReaderText>.paragraphs(): List<String> =
        filterIsInstance<ReaderText.Text>().map { it.line.text }

    private fun epub(vararg entries: Pair<String, Any>): File {
        val book = temp.newFile()
        ZipOutputStream(book.outputStream()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(if (content is ByteArray) content else content.toString().toByteArray())
                out.closeEntry()
            }
        }
        return book
    }

    private fun opf(vararg spine: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
          <manifest>
            ${spine.withIndex().joinToString("") { (i, href) -> """<item id="c$i" href="$href" media-type="application/xhtml+xml"/>""" }}
          </manifest>
          <spine>
            ${spine.indices.joinToString("") { i -> """<itemref idref="c$i"/>""" }}
          </spine>
        </package>
    """.trimIndent()

    private fun ncx(vararg navPoints: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>${navPoints.joinToString("")}</navMap>
        </ncx>
    """.trimIndent()

    private var navPointId = 0

    private fun navPoint(title: String, src: String, vararg children: String): String =
        """<navPoint id="n${navPointId++}"><navLabel><text>$title</text></navLabel>""" +
                """<content src="$src"/>${children.joinToString("")}</navPoint>"""

    private fun xhtml(body: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
                """<html xmlns="http://www.w3.org/1999/xhtml"><body>$body</body></html>"""
}
