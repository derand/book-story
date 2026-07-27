/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * `keepImageBytes`: with the reader's images turned off the parse must still
 * describe every image (the slot is reserved from its size, and the parsed text
 * is cached and reused once images are turned back on) but must not hold on to
 * the encoded bytes, which are tens of MB on an image-heavy book.
 */
@RunWith(AndroidJUnit4::class)
class DocumentParserImageBytesTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    /** A 3x2 PNG. */
    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAEElEQVR4nGP4" +
            "z8AAQQxwFgBB0gX7h/C5SAAAAABJRU5ErkJggg=="

    @Test
    fun keptBytesAreTheImageItself() {
        val image = parseImage(keepImageBytes = true)

        assertTrue("bytes are kept", image.image.bytes.isNotEmpty())
        assertEquals(3, image.image.width)
        assertEquals(2, image.image.height)
    }

    @Test
    fun droppedBytesLeaveTheImageAndItsSizeInPlace() {
        val image = parseImage(keepImageBytes = false)

        assertTrue("bytes are dropped", image.image.bytes.isEmpty())
        assertEquals("width survives", 3, image.image.width)
        assertEquals("height survives", 2, image.image.height)
        assertEquals("src survives", "pic.png", image.image.src)
    }

    @Test
    fun identityDoesNotDependOnWhetherBytesAreKept() {
        // The id is the parse-cache key of an image: it has to match across a
        // parse done with images off and one done with them on.
        assertEquals(
            parseImage(keepImageBytes = true).image.id,
            parseImage(keepImageBytes = false).image.id
        )
    }

    private fun parseImage(keepImageBytes: Boolean): ReaderText.Image = runBlocking {
        val document = Jsoup.parse(
            """
            <FictionBook>
                <body>
                    <section>
                        <title><p>Chapter</p></title>
                        <p>Text around the picture.</p>
                        <image l:href="#pic.png"/>
                    </section>
                </body>
            </FictionBook>
            """.trimIndent(),
            "",
            Parser.xmlParser()
        )

        documentParser.parseDocument(
            document = document,
            base64Images = mapOf("pic.png" to png),
            keepImageBytes = keepImageBytes
        ).filterIsInstance<ReaderText.Image>().single()
    }
}
