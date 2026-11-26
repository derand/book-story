/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.parser.file.EpubFileParser
import ua.acclorite.book_story.data.parser.file.Fb2FileParser
import ua.acclorite.book_story.data.parser.file.FileParserImpl
import ua.acclorite.book_story.data.parser.file.HtmlFileParser
import ua.acclorite.book_story.data.parser.file.PdfFileParser
import ua.acclorite.book_story.data.parser.file.TxtFileParser
import ua.acclorite.book_story.domain.model.library.Book

/**
 * The title, author and description a book gets at import. They are read once
 * and kept in the DB from then on, so a regression here damages every book
 * imported while it lasts, and a later fix does not repair those books.
 *
 * Goes through [FileParserImpl], as the import does, so the format dispatch is
 * covered too.
 */
@RunWith(RobolectricTestRunner::class)
class FileParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val parser = FileParserImpl(
        txtFileParser = TxtFileParser(),
        pdfFileParser = PdfFileParser(RuntimeEnvironment.getApplication()),
        epubFileParser = EpubFileParser(),
        fb2FileParser = Fb2FileParser(),
        htmlFileParser = HtmlFileParser(),
    )

    // --- the tag-coverage books ---

    @Test
    fun fb2TagCoverageBook() {
        val book = parse(resourceFile("fb2/tags-test.fb2"))

        assertEquals("FB2 Tag Coverage Test", book.title)
        assertEquals(
            "A test book: every section checks one FB2 tag. " +
                "Under each example it says what must be visible.",
            book.description
        )
    }

    @Test
    fun epubTagCoverageBook() {
        val book = parse(zipDirectory(resourceFile("epub/tags-test"), "tags-test.epub"))

        assertEquals("EPUB Tag Coverage Test", book.title)
        assertEquals("derand", book.author.getAsString())
        assertEquals(
            "Hand-written EPUB exercising the inline and block tags the reader handles. " +
                "All text original.",
            book.description
        )
    }

    @Test
    fun htmlTagCoverageBook() {
        val book = parse(resourceFile("html/tags-test.html"))

        assertEquals("HTML Tag Coverage Test", book.title)
        assertUnknownAuthor(book)
        assertNull(book.description)
    }

    @Test
    fun markdownBookIsNamedAfterItsFile() {
        val book = parse(resourceFile("md/tags-test.md"))

        assertEquals("tags-test", book.title)
        assertUnknownAuthor(book)
        assertNull(book.description)
    }

    // --- FB2 ---

    @Test
    fun fb2CyrillicAuthorWithSpacedNameParts() {
        val book = parse(
            fb2(
                titleInfo = """
                    <author>
                      <first-name>Іван</first-name>
                      <last-name>Петренко</last-name>
                    </author>
                    <book-title>Тестова книга</book-title>
                """
            )
        )

        assertEquals("Тестова книга", book.title)
        assertEquals("Іван Петренко", book.author.getAsString())
    }

    @Test
    fun fb2WithoutAuthorTitleOrAnnotation() {
        val book = parse(fb2(titleInfo = "<genre>prose</genre>", name = "no-metadata.fb2"))

        assertEquals("no-metadata", book.title)
        assertUnknownAuthor(book)
        assertNull(book.description)
    }

    @Test
    fun fb2BlankTitleFallsBackToTheFileName() {
        val book = parse(fb2(titleInfo = "<book-title>   </book-title>", name = "blank.fb2"))

        assertEquals("blank", book.title)
    }

    // --- EPUB ---

    @Test
    fun epubWithoutCreatorTitleOrDescription() {
        val book = parse(epub(metadata = "<dc:language>en</dc:language>", name = "bare.epub"))

        assertEquals("bare", book.title)
        assertUnknownAuthor(book)
        assertNull(book.description)
    }

    @Test
    fun epubDescriptionIsPlainText() {
        val book = parse(
            epub(
                metadata = """
                    <dc:title>Book</dc:title>
                    <dc:description>&lt;p&gt;One &lt;b&gt;bold&lt;/b&gt; word.&lt;/p&gt;</dc:description>
                """
            )
        )

        assertEquals("One bold word.", book.description)
    }

    // --- plain text, HTML ---

    @Test
    fun txtBookIsNamedAfterItsFile() {
        val book = parse(file("Нотатки.txt", "First line\nSecond line"))

        assertEquals("Нотатки", book.title)
        assertUnknownAuthor(book)
        assertNull(book.description)
    }

    @Test
    fun htmlWithoutTitleIsNamedAfterItsFile() {
        val book = parse(file("page.htm", "<html><body><p>Text</p></body></html>"))

        assertEquals("page", book.title)
    }

    // --- dispatch ---

    @Test
    fun aDoubleExtensionGoesToTheFormatBeforeIt() {
        val book = parse(
            fb2(titleInfo = "<book-title>Inner</book-title>", name = "book.fb2.xml")
        )

        assertEquals("Inner", book.title)
    }

    @Test
    fun theFormatIsReadIgnoringCase() {
        val book = parse(
            fb2(titleInfo = "<book-title>Upper</book-title>", name = "BOOK.FB2")
        )

        assertEquals("Upper", book.title)
    }

    @Test
    fun anUnsupportedFormatIsNotABook() {
        assertNull(parseOrNull(file("notes.docx", "text")))
    }

    // --- helpers ---

    private fun parse(file: File): Book =
        parseOrNull(file) ?: error("${file.name} did not parse")

    private fun parseOrNull(file: File): Book? = runBlocking {
        parser.parse(CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), file))
    }

    private fun assertUnknownAuthor(book: Book) {
        val author = book.author
        assertTrue("author is $author", author is UIText.StringResource)
        assertEquals(R.string.unknown_author, (author as UIText.StringResource).resId)
    }

    private fun file(name: String, text: String): File =
        temp.newFile(name).apply { writeText(text) }

    private fun fb2(titleInfo: String, name: String = "book.fb2"): File = file(
        name,
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  $titleInfo
                </title-info>
              </description>
              <body><section><p>Text.</p></section></body>
            </FictionBook>
        """.trimIndent()
    )

    private fun epub(metadata: String, name: String = "book.epub"): File {
        val dir = temp.newFolder()
        File(dir, "mimetype").writeText("application/epub+zip")
        File(dir, "META-INF").mkdir()
        File(dir, "META-INF/container.xml").writeText(
            """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent()
        )
        File(dir, "OEBPS").mkdir()
        File(dir, "OEBPS/content.opf").writeText(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    $metadata
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent()
        )
        File(dir, "OEBPS/c1.xhtml").writeText(
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Text.</p></body></html>"
        )
        return zipDirectory(dir, name)
    }

    private fun zipDirectory(source: File, name: String): File {
        val zip = File(temp.newFolder(), name)
        ZipOutputStream(zip.outputStream()).use { out ->
            source.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(source).invariantSeparatorsPath }
                .forEach { file ->
                    out.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    out.write(file.readBytes())
                    out.closeEntry()
                }
        }
        return zip
    }
}
