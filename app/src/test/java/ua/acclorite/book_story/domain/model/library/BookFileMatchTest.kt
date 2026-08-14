/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which book a file arriving from another app belongs to. Getting this wrong is
 * silent both ways: too loose and a tap opens someone else's book, too strict
 * and the library quietly fills with duplicates of books already in it.
 */
class BookFileMatchTest {

    private fun book(id: Int, filePath: String) = Book.default.copy(
        id = id,
        filePath = filePath
    )

    private val library = listOf(
        book(1, "/storage/emulated/0/Books/Solaris.fb2"),
        book(2, "/storage/emulated/0/Download/Solaris.fb2"),
        book(3, "/storage/emulated/0/Books/Nebula.epub")
    )

    @Test
    fun `path wins over name`() {
        val match = library.findForFile(
            filePath = "/storage/emulated/0/Download/Solaris.fb2",
            fileName = "Solaris.fb2"
        )

        // Both rows carry that name; only one carries that path.
        assertEquals(2, match?.id)
    }

    @Test
    fun `falls back to the file name when there is no path`() {
        val match = library.findForFile(filePath = "", fileName = "Nebula.epub")
        assertEquals(3, match?.id)
    }

    @Test
    fun `falls back to the file name when the path is unknown to the library`() {
        // The same file reached through another provider reports another path.
        val match = library.findForFile(
            filePath = "/mnt/media_rw/ABCD-1234/Nebula.epub",
            fileName = "Nebula.epub"
        )

        assertEquals(3, match?.id)
    }

    @Test
    fun `a file the library does not have matches nothing`() {
        val match = library.findForFile(
            filePath = "/storage/emulated/0/Books/Eden.fb2",
            fileName = "Eden.fb2"
        )

        assertNull(match)
    }

    @Test
    fun `nothing to match on matches nothing`() {
        assertNull(library.findForFile(filePath = "", fileName = ""))
    }

    @Test
    fun `a name is matched whole, not as a suffix`() {
        // "Solaris.fb2" must not be found by "olaris.fb2", which a LIKE '%…'
        // over the path would do.
        assertNull(library.findForFile(filePath = "", fileName = "olaris.fb2"))
    }

    @Test
    fun `an empty library matches nothing`() {
        val match = emptyList<Book>().findForFile(
            filePath = "/storage/emulated/0/Books/Solaris.fb2",
            fileName = "Solaris.fb2"
        )

        assertNull(match)
    }
}
