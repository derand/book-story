/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The guard that decides whether a parsed document is empty. It used to ask
 * whether the document had any *text*, which is not the same question: a page
 * holding only an image — the shape of every Calibre-made cover — was thrown
 * away with its image.
 */
@RunWith(AndroidJUnit4::class)
class EmptyDocumentGuardTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    /** A 3x2 PNG. */
    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAEElEQVR4nGP4" +
            "z8AAQQxwFgBB0gX7h/C5SAAAAABJRU5ErkJggg=="

    // <img> is the HTML/EPUB spelling and resolves against the book's zip, not
    // against FB2's base64 binaries — so the fixture has to be a real archive.
    private lateinit var archive: File
    private lateinit var zip: ZipFile

    @Before
    fun packTheImage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        archive = File(app.cacheDir, "empty-guard-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { out ->
            out.putNextEntry(ZipEntry("images/pic.png"))
            out.write(Base64.decode(png, Base64.DEFAULT))
            out.closeEntry()
        }
        zip = ZipFile(archive)
    }

    @After
    fun cleanUp() {
        zip.close()
        archive.delete()
    }

    @Test
    fun aPageHoldingOnlyAnImageSurvives() {
        val parsed = parse("<p><img src=\"pic.png\"/></p>")

        assertEquals("one entry, the image", 1, parsed.size)
        assertTrue("it is the image", parsed.single() is ReaderText.Image)
    }

    @Test
    fun theCoverShapeSurvives() {
        // What a Calibre-converted EPUB puts in its title page: an <image>
        // inside an <svg>, and no text anywhere in the document
        val parsed = parse(
            """
            <div>
              <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
                   width="100%" height="100%" viewBox="0 0 3 2">
                <image width="3" height="2" xlink:href="pic.png"/>
              </svg>
            </div>
            """.trimIndent()
        )

        assertEquals("the cover is kept", 1, parsed.filterIsInstance<ReaderText.Image>().size)
    }

    @Test
    fun anImageWithTextAroundItStillWorks() {
        val parsed = parse("<p>Перед</p><p><img src=\"pic.png\"/></p><p>Після</p>")

        assertEquals(2, parsed.filterIsInstance<ReaderText.Text>().size)
        assertEquals(1, parsed.filterIsInstance<ReaderText.Image>().size)
    }

    @Test
    fun aDocumentWithNothingInItIsStillDropped() {
        assertTrue("nothing to show", parse("<div></div>").isEmpty())
    }

    @Test
    fun aDocumentOfWhitespaceIsStillDropped() {
        assertTrue("whitespace is not content", parse("<p>   </p><p>\n\t</p>").isEmpty())
    }

    @Test
    fun anImageWhoseSourceIsMissingIsNotContent() {
        // Nothing was decoded, so there is genuinely nothing to show
        assertTrue(parse("<p><img src=\"absent.png\"/></p>").isEmpty())
    }

    /** The EPUB path: the chapter comes from the TOC, so none is required here. */
    private fun parse(body: String): List<ReaderText> = runBlocking {
        documentParser.parseDocument(
            document = Jsoup.parse("<html><body>$body</body></html>", "", Parser.htmlParser()),
            zipFile = zip,
            imageEntries = zip.entries().toList(),
            includeChapter = false
        )
    }
}
