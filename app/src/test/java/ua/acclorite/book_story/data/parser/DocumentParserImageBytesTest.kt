/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.parser.document.Base64Images
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * `keepImageBytes`: with the reader's images turned off the parse must still
 * describe every image (the slot is reserved from its size, and the parsed text
 * is cached and reused once images are turned back on) but must not hold on to
 * the encoded bytes, which are tens of MB on an image-heavy book.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentParserImageBytesTest {

    private val documentParser = DocumentParser()

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

    private fun parseImage(keepImageBytes: Boolean): ReaderText.Image = documentParser
        .parseFb2Body(
            """
            <section>
                <title><p>Chapter</p></title>
                <p>Text around the picture.</p>
                <image l:href="#pic.png"/>
            </section>
            """.trimIndent(),
            images = Base64Images(mapOf("pic.png" to TINY_PNG_BASE64)),
            keepImageBytes = keepImageBytes
        )
        .filterIsInstance<ReaderText.Image>()
        .single()
}
