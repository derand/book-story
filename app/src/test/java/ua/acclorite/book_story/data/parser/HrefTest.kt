/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class HrefTest {

    @Test
    fun percentEscapesAreDecoded() {
        assertEquals("my chapter.xhtml", decodeHref("my%20chapter.xhtml"))
        assertEquals("Éa.jpg", decodeHref("%C3%89a.jpg"))
        assertEquals("кіт.png", decodeHref("%D0%BA%D1%96%D1%82.png"))
        assertEquals("a/b", decodeHref("a%2fb"))
    }

    @Test
    fun aStrayPercentIsKeptAndDoesNotThrow() {
        assertEquals("pic50%.png", decodeHref("pic50%.png"))
        assertEquals("100%-ch.xhtml", decodeHref("100%-ch.xhtml"))
        assertEquals("a%", decodeHref("a%"))
        assertEquals("a%4", decodeHref("a%4"))
        assertEquals("%zz", decodeHref("%zz"))
    }

    @Test
    fun plusIsAPlus() {
        assertEquals("c++.xhtml", decodeHref("c++.xhtml"))
        assertEquals("a+b c.jpg", decodeHref("a+b%20c.jpg"))
    }

    @Test
    fun decodedOnceSoAnEscapedPercentStaysAPercent() {
        assertEquals("50%25.png", decodeHref("50%2525.png"))
        assertEquals("50%.png", decodeHref("50%25.png"))
    }

    @Test
    fun aNonAsciiCharacterNextToEscapesSurvives() {
        assertEquals("ї é😀", decodeHref("ї%20%C3%A9😀"))
    }

    // --- resolveHref ---

    @Test
    fun aRelativeHrefResolvesAgainstTheFolderOfItsFile() {
        assertEquals("OEBPS/Images/a.jpg", resolveHref("OEBPS/Text/c1.xhtml", "../Images/a.jpg"))
        assertEquals("OEBPS/Text/c2.xhtml", resolveHref("OEBPS/Text/c1.xhtml", "c2.xhtml"))
        assertEquals("OEBPS/Text/c2.xhtml", resolveHref("OEBPS/Text/c1.xhtml", "./c2.xhtml"))
        assertEquals("a.jpg", resolveHref("c1.xhtml", "a.jpg"))
    }

    @Test
    fun fragmentAndQueryAreDroppedAndThePathDecodedOnce() {
        assertEquals("OEBPS/my chapter.xhtml", resolveHref("OEBPS/toc.ncx", "my%20chapter.xhtml#start"))
        assertEquals("OEBPS/a.jpg", resolveHref("OEBPS/c.xhtml", "a.jpg?v=2"))
        assertEquals("OEBPS/50%.png", resolveHref("OEBPS/c.xhtml", "50%25.png"))
        assertEquals("OEBPS/50%.png", resolveHref("OEBPS/c.xhtml", "50%.png"))
    }

    @Test
    fun dotDotNeverClimbsAboveTheRoot() {
        assertEquals("a.jpg", resolveHref("OEBPS/c.xhtml", "../../../a.jpg"))
    }

    @Test
    fun aLeadingSlashIsReadFromTheRoot() {
        assertEquals("Images/a.jpg", resolveHref("OEBPS/Text/c.xhtml", "/Images/a.jpg"))
    }

    @Test
    fun nothingInTheContainerResolvesToNull() {
        assertEquals(null, resolveHref("OEBPS/c.xhtml", ""))
        assertEquals(null, resolveHref("OEBPS/c.xhtml", "  "))
        assertEquals(null, resolveHref("OEBPS/c.xhtml", "#note1"))
        assertEquals(null, resolveHref("OEBPS/c.xhtml", "https://example.com/a.jpg"))
        assertEquals(null, resolveHref("OEBPS/c.xhtml", "mailto:a@b.c"))
        assertEquals(null, resolveHref("OEBPS/c.xhtml", "data:image/png;base64,AAAA"))
    }

    @Test
    fun containerFullPathIsFromTheRoot() {
        assertEquals("OEBPS/content.opf", resolveHref("", "OEBPS/content.opf"))
    }
}
