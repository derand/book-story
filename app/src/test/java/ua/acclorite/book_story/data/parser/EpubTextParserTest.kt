/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
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
        DocumentParser()
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
    fun theFirstOfSeveralNavPointsOnOneDocumentNamesIt() {
        // They used to be joined with " / ": a single-file book (a Google Docs
        // export) got every heading of the book in one title
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Перша назва", "one.xhtml"),
                navPoint("Друга назва", "one.xhtml#middle"),
                navPoint("Третя назва", "one.xhtml#end")
            ),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Перша назва"), parse(book).chapterTitles())
    }

    @Test
    fun aSingleFileBookWithANavKeepsItsFirstLineAsText() {
        val book = epub(
            "OEBPS/content.opf" to opf("book.xhtml", nav = "nav.xhtml"),
            "OEBPS/nav.xhtml" to nav(
                """<li><a href="book.xhtml#h0">Назва книги</a></li>""" +
                        """<li><a href="book.xhtml#h1">Частина перша</a></li>""" +
                        """<li><a href="book.xhtml#h2">Частина друга</a></li>"""
            ),
            "OEBPS/book.xhtml" to xhtml("<p>Застереження.</p><p>Текст.</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Назва книги"), text.chapterTitles())
        assertEquals(listOf("Застереження.", "Текст."), text.paragraphs())
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

    // --- hrefs (#136) ---

    @Test
    fun aStrayPercentInAnImageSrcDoesNotEmptyTheBook() {
        // URLDecoder threw on "50%." and the whole book came out empty
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p><img src=\"Images/pic50%.png\"/>"),
            "OEBPS/Images/pic50%.png" to TINY_PNG
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ"), text.chapterTitles())
        assertEquals(listOf("текст"), text.paragraphs())
        assertEquals(
            "OEBPS/Images/pic50%.png",
            text.filterIsInstance<ReaderText.Image>().single().image.src
        )
    }

    @Test
    fun aStrayPercentInTheTocOrTheManifestDoesNotEmptyTheBook() {
        val book = epub(
            "OEBPS/content.opf" to opf("100%-ch.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "100%-ch.xhtml")),
            "OEBPS/100%-ch.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Розділ"), parse(book).chapterTitles())
    }

    @Test
    fun aPlusInAnHrefIsAPlus() {
        val book = epub(
            "OEBPS/content.opf" to opf("c++.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "c++.xhtml")),
            "OEBPS/c++.xhtml" to xhtml("<p>текст</p><img src=\"a+b.png\"/>"),
            "OEBPS/a+b.png" to TINY_PNG
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ"), text.chapterTitles())
        assertEquals("OEBPS/a+b.png", text.filterIsInstance<ReaderText.Image>().single().image.src)
    }

    @Test
    fun anEscapedPercentInTheManifestIsDecodedOnce() {
        // Decoded twice, "%25" became "%" and then threw
        val book = epub(
            "OEBPS/content.opf" to opf("50%2525.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "50%2525.xhtml")),
            "OEBPS/50%25.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Розділ"), parse(book).chapterTitles())
    }

    @Test
    fun anEscapedUpperCaseLetterMatchesItsFile() {
        // Lowercased before decoding, "%C3%89" stayed "É" and missed "é"
        val book = epub(
            "OEBPS/content.opf" to opf("%C3%89tude.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "%C3%89tude.xhtml")),
            "OEBPS/Étude.xhtml" to xhtml("<p>текст</p><img src=\"%C3%89.png\"/>"),
            "OEBPS/É.png" to TINY_PNG
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ"), text.chapterTitles())
        assertEquals(1, text.filterIsInstance<ReaderText.Image>().size)
    }

    // --- the package: container.xml → OPF → full paths (#136) ---

    @Test
    fun theOpfIsTheOneContainerXmlNamesNotTheFirstInTheArchive() {
        val book = epub(
            "backup/old.opf" to opf("old.xhtml"),
            "backup/old.xhtml" to xhtml("<p>Стара версія</p><p>текст</p>"),
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf("new.xhtml"),
            "OEBPS/new.xhtml" to xhtml("<p>Нова версія</p><p>текст</p>")
        )

        assertEquals(listOf("Нова версія"), parse(book).chapterTitles())
    }

    @Test
    fun manifestHrefsResolveAgainstTheOpfsOwnFolder() {
        val book = epub(
            "META-INF/container.xml" to container("OPS/package/content.opf"),
            "OPS/package/content.opf" to opf("../text/one.xhtml"),
            "OPS/text/one.xhtml" to xhtml("<p>Розділ</p><p>текст</p><img src=\"../img/a.png\"/>"),
            "OPS/img/a.png" to TINY_PNG
        )

        val text = parse(book).text
        assertEquals(listOf("Розділ"), text.chapterTitles())
        assertEquals("OPS/img/a.png", text.filterIsInstance<ReaderText.Image>().single().image.src)
    }

    @Test
    fun twoDocumentsWithOneNameInTwoFoldersKeepTheirOwnTitlesAndImages() {
        // Matched by file name, both got one title and one picture
        val book = epub(
            "OEBPS/content.opf" to opf("a/ch1.xhtml", "b/ch1.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                navPoint("Перша частина", "a/ch1.xhtml"),
                navPoint("Друга частина", "b/ch1.xhtml")
            ),
            "OEBPS/a/ch1.xhtml" to xhtml("<p>текст а</p><img src=\"pic.png\"/>"),
            "OEBPS/a/pic.png" to TINY_PNG,
            "OEBPS/b/ch1.xhtml" to xhtml("<p>текст б</p><img src=\"pic.png\"/>"),
            "OEBPS/b/pic.png" to TINY_PNG
        )

        val text = parse(book).text
        assertEquals(listOf("Перша частина", "Друга частина"), text.chapterTitles())
        assertEquals(listOf("текст а", "текст б"), text.paragraphs())
        assertEquals(
            listOf("OEBPS/a/pic.png", "OEBPS/b/pic.png"),
            text.filterIsInstance<ReaderText.Image>().map { it.image.src }
        )
    }

    @Test
    fun aPathOneLevelOffStillFindsTheOnlyFileOfThatName() {
        // What matching by file name used to forgive
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p><img src=\"../Images/cover.png\"/>"),
            "OEBPS/Images/cover.png" to TINY_PNG
        )

        assertEquals(
            "OEBPS/Images/cover.png",
            parse(book).text.filterIsInstance<ReaderText.Image>().single().image.src
        )
    }

    @Test
    fun aPathOffToANameTwoFilesShareIsNotGuessed() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p><img src=\"../x/pic.png\"/>"),
            "OEBPS/a/pic.png" to TINY_PNG,
            "OEBPS/b/pic.png" to TINY_PNG
        )

        assertTrue(parse(book).text.none { it is ReaderText.Image })
    }

    @Test
    fun anHrefInAnotherCaseStillFindsItsFile() {
        val book = epub(
            "OEBPS/content.opf" to opf("Text/One.xhtml"),
            "OEBPS/toc.ncx" to ncx(navPoint("Розділ", "text/one.XHTML")),
            "OEBPS/text/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Розділ"), parse(book).chapterTitles())
    }

    // --- the TOC: EPUB 3 nav, else the NCX the spine names ---

    @Test
    fun anEpub3BookWithOnlyANavDocumentGetsItsTitlesAndNesting() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml", "two.xhtml", nav = "nav.xhtml"),
            "OEBPS/nav.xhtml" to nav(
                """<li><a href="one.xhtml">Частина</a><ol>""" +
                        """<li><a href="two.xhtml#s1">Розділ</a></li></ol></li>"""
            ),
            "OEBPS/one.xhtml" to xhtml("<p>текст один</p>"),
            "OEBPS/two.xhtml" to xhtml("<p>текст два</p>")
        )

        val chapters = parse(book).text.filterIsInstance<ReaderText.Chapter>()
        assertEquals(listOf("Частина", "Розділ"), chapters.map { it.title })
        assertEquals(listOf(0, 1), chapters.map { it.depth })
    }

    @Test
    fun theNavDocumentWinsOverTheNcx() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml", nav = "nav.xhtml", ncx = "toc.ncx"),
            "OEBPS/nav.xhtml" to nav("""<li><a href="one.xhtml">З nav</a></li>"""),
            "OEBPS/toc.ncx" to ncx(navPoint("З NCX", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("З nav"), parse(book).chapterTitles())
    }

    @Test
    fun theNavOfTypeTocIsReadNotTheLandmarks() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml", nav = "nav.xhtml"),
            "OEBPS/nav.xhtml" to
                    """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>""" +
                    """<nav epub:type="landmarks"><ol><li><a href="one.xhtml">Початок</a></li></ol></nav>""" +
                    """<nav epub:type="toc"><ol><li><a href="one.xhtml">Розділ</a></li></ol></nav>""" +
                    "</body></html>",
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Розділ"), parse(book).chapterTitles())
    }

    @Test
    fun theNcxIsTheOneTheSpineNames() {
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml", ncx = "real.ncx"),
            "OEBPS/a-stray.ncx" to ncx(navPoint("Чужий", "one.xhtml")),
            "OEBPS/real.ncx" to ncx(navPoint("Справжній", "one.xhtml")),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Справжній"), parse(book).chapterTitles())
    }

    @Test
    fun ncxSourcesResolveAgainstTheNcxsOwnFolder() {
        val book = epub(
            "OEBPS/content.opf" to opf("text/one.xhtml", ncx = "toc/toc.ncx"),
            "OEBPS/toc/toc.ncx" to ncx(navPoint("Розділ", "../text/one.xhtml")),
            "OEBPS/text/one.xhtml" to xhtml("<p>текст</p>")
        )

        assertEquals(listOf("Розділ"), parse(book).chapterTitles())
    }

    @Test
    fun aNavPointUnderAParentWithoutASourceIsKept() {
        // It used to be dropped together with its title
        val book = epub(
            "OEBPS/content.opf" to opf("one.xhtml"),
            "OEBPS/toc.ncx" to ncx(
                """<navPoint id="p"><navLabel><text>Група</text></navLabel><content src=""/>""" +
                        navPoint("Розділ", "one.xhtml") + "</navPoint>"
            ),
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        val chapters = parse(book).text.filterIsInstance<ReaderText.Chapter>()
        assertEquals(listOf("Розділ"), chapters.map { it.title })
        assertEquals(listOf(0), chapters.map { it.depth })
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
            "OEBPS/cover.xhtml" to xhtml("<div><img src=\"Images/cover.png\"/></div>"),
            "OEBPS/Images/cover.png" to TINY_PNG,
            "OEBPS/one.xhtml" to xhtml("<p>текст</p>")
        )

        val text = parse(book).text
        assertEquals(listOf("Обкладинка", "Розділ"), text.chapterTitles())
        val image = text.filterIsInstance<ReaderText.Image>().single()
        assertEquals("OEBPS/Images/cover.png", image.image.src)
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
            "OEBPS/cover.png" to TINY_PNG,
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

    private fun opf(vararg spine: String, nav: String? = null, ncx: String? = null): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="${if (nav != null) "3.0" else "2.0"}">
          <manifest>
            ${spine.withIndex().joinToString("") { (i, href) -> """<item id="c$i" href="$href" media-type="application/xhtml+xml"/>""" }}
            ${nav?.let { """<item id="nav" href="$it" media-type="application/xhtml+xml" properties="nav"/>""" }.orEmpty()}
            ${ncx?.let { """<item id="ncx" href="$it" media-type="application/x-dtbncx+xml"/>""" }.orEmpty()}
          </manifest>
          <spine${ncx?.let { " toc=\"ncx\"" }.orEmpty()}>
            ${spine.indices.joinToString("") { i -> """<itemref idref="c$i"/>""" }}
          </spine>
        </package>
    """.trimIndent()

    private fun container(opfPath: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun nav(items: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>""" +
                """<nav epub:type="toc"><ol>$items</ol></nav></body></html>"""

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
