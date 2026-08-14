/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which file names are books.
 *
 * The rule reaches past one unknown extension and no further, and it looks at
 * one position rather than searching the name. Both halves matter: too short a
 * reach and Drive's `.fb2.xml` is not a book; too loose a search and a web page
 * about books is handed to the FB2 parser.
 */
class ExtensionsDataTest {

    @Test
    fun `a plain extension is the format`() {
        assertEquals(".fb2", ExtensionsData.formatOf("Solaris.fb2"))
        assertEquals(".epub", ExtensionsData.formatOf("Solaris.epub"))
        assertEquals(".md", ExtensionsData.formatOf("notes.md"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(".fb2", ExtensionsData.formatOf("Solaris.FB2"))
        assertEquals(".epub", ExtensionsData.formatOf("Solaris.EpUb"))
    }

    @Test
    fun `the second-to-last extension is read past an unknown one`() {
        // What Google Drive does to an FB2, and the reason this exists.
        assertEquals(".fb2", ExtensionsData.formatOf("Solaris.fb2.xml"))
    }

    @Test
    fun `only the second-to-last counts, not any extension in the name`() {
        // An HTML page about converting books. A rule that searched the name
        // would find ".fb2" and hand a web page to the FB2 parser.
        assertEquals(
            ".html",
            ExtensionsData.formatOf("як конвертувати .fb2 в .epub.html.xml")
        )
    }

    @Test
    fun `two unknown extensions is not a book`() {
        assertNull(ExtensionsData.formatOf("Solaris.fb2.xml.bak"))
    }

    @Test
    fun `an unknown extension over an unknown one is not a book`() {
        assertNull(ExtensionsData.formatOf("archive.tar.gz"))
    }

    @Test
    fun `a known extension under an unknown one is taken deliberately`() {
        // A backup of a book is still a book, and the user tapped it.
        assertEquals(".epub", ExtensionsData.formatOf("Solaris.epub.bak"))
    }

    @Test
    fun `a name with no extension is not a book`() {
        assertNull(ExtensionsData.formatOf("Solaris"))
        assertNull(ExtensionsData.formatOf(""))
    }

    @Test
    fun `a trailing dot is not an extension`() {
        assertNull(ExtensionsData.formatOf("Solaris."))
    }

    @Test
    fun `dots inside the title do not confuse it`() {
        assertEquals(".fb2", ExtensionsData.formatOf("Solaris. Эдем. Непобедимый.fb2"))
        assertEquals(".fb2", ExtensionsData.formatOf("Solaris. Эдем. Непобедимый.fb2.xml"))
    }

    @Test
    fun `a file that is only an extension still names its format`() {
        assertEquals(".fb2", ExtensionsData.formatOf(".fb2"))
    }
}
