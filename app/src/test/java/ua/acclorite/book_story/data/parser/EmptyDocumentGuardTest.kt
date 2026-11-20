/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.epub.EpubPackage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The guard that decides whether a parsed document is empty. It used to ask
 * whether the document had any *text*, which is not the same question: a page
 * holding only an image — the shape of every Calibre-made cover — was thrown
 * away with its image.
 */
@RunWith(RobolectricTestRunner::class)
class EmptyDocumentGuardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val documentParser = DocumentParser()

    // <img> is the HTML/EPUB spelling and resolves against the book's zip, not
    // against FB2's base64 binaries — so the fixture has to be a real archive.
    private lateinit var zip: ZipFile

    @Before
    fun packTheImage() {
        val archive = temp.newFile("empty-guard-test.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("OEBPS/images/pic.png"))
            out.write(TINY_PNG)
            out.closeEntry()
        }
        zip = ZipFile(archive)
    }

    @After
    fun closeTheArchive() {
        zip.close()
    }

    @Test
    fun aPageHoldingOnlyAnImageSurvives() {
        val parsed = parse("<p><img src=\"../images/pic.png\"/></p>")

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
                <image width="3" height="2" xlink:href="../images/pic.png"/>
              </svg>
            </div>
            """.trimIndent()
        )

        assertEquals("the cover is kept", 1, parsed.filterIsInstance<ReaderText.Image>().size)
    }

    @Test
    fun anImageWithTextAroundItStillWorks() {
        val parsed = parse("<p>Перед</p><p><img src=\"../images/pic.png\"/></p><p>Після</p>")

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
    private fun parse(body: String): List<ReaderText> = documentParser.parseXhtmlBody(
        body,
        images = EpubPackage.open(zip).imagesOf(ZipEntry("OEBPS/text/chapter.xhtml"))
    )
}
