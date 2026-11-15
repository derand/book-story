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
}
