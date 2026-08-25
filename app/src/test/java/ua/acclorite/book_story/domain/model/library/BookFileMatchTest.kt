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
import ua.acclorite.book_story.core.ui.UIText

/**
 * Whether a file already is a book in the library. Getting it wrong costs
 * either a second row for one book, or a file the user cannot import because
 * something unrelated is standing in for it.
 */
class BookFileMatchTest {

    private val drive = "com.google.android.apps.docs.storage"

    private fun book(
        id: Int,
        filePath: String,
        documentAuthority: String? = null,
        documentId: String? = null
    ) = Book.default.copy(
        id = id,
        title = "Book $id",
        author = UIText.StringValue(""),
        filePath = filePath,
        documentAuthority = documentAuthority,
        documentId = documentId
    )

    @Test
    fun `the identity matches whatever the paths say`() {
        val books = listOf(
            book(1, "/storage/solaris.fb2", drive, "acc=1;doc=A"),
            book(2, "/storage/solaris.fb2", drive, "acc=1;doc=B")
        )

        assertEquals(
            2,
            books.findForFile(
                filePath = "/storage/solaris.fb2",
                fileName = "solaris.fb2",
                documentAuthority = drive,
                documentId = "acc=1;doc=B"
            )?.id
        )
    }

    /**
     * The defect this replaces. Two folders on one cloud provider compose every
     * path under them from the same invented string, so an unrelated book can
     * carry the path of the file being looked up.
     */
    @Test
    fun `a book known by its id is not claimed by a matching path`() {
        val books = listOf(book(1, "/storage/solaris.fb2", drive, "acc=1;doc=A"))

        assertNull(
            books.findForFile(
                filePath = "/storage/solaris.fb2",
                fileName = "solaris.fb2",
                documentAuthority = drive,
                documentId = "acc=1;doc=ELSEWHERE"
            )
        )
    }

    /**
     * Every row written before an identity was stored, until the first time it
     * is opened. The path has to keep answering for those, or a whole library
     * would offer itself for import again.
     */
    @Test
    fun `a book with no identity still matches by path`() {
        val books = listOf(book(1, "/storage/emulated/0/Books/solaris.fb2"))

        assertEquals(
            1,
            books.findForFile(
                filePath = "/storage/emulated/0/Books/solaris.fb2",
                fileName = "solaris.fb2",
                documentAuthority = drive,
                documentId = "acc=1;doc=A"
            )?.id
        )
    }

    @Test
    fun `an identity of another authority is a different document`() {
        val books = listOf(book(1, "/storage/solaris.fb2", drive, "acc=1;doc=A"))

        assertNull(
            books.findForFile(
                filePath = "",
                fileName = "",
                documentAuthority = "com.android.externalstorage.documents",
                documentId = "acc=1;doc=A"
            )
        )
    }

    @Test
    fun `a file with no identity and no path falls back to its name`() {
        val books = listOf(book(1, "/storage/emulated/0/Books/solaris.fb2"))

        assertEquals(
            1,
            books.findForFile(filePath = "", fileName = "solaris.fb2")?.id
        )
    }

    @Test
    fun `nothing matches an empty library`() {
        assertNull(
            emptyList<Book>().findForFile(
                filePath = "/storage/solaris.fb2",
                fileName = "solaris.fb2",
                documentAuthority = drive,
                documentId = "acc=1;doc=A"
            )
        )
    }

    @Test
    fun `a blank name matches nothing once path and identity have failed`() {
        val books = listOf(book(1, "/storage/emulated/0/Books/solaris.fb2"))

        assertNull(books.findForFile(filePath = "", fileName = ""))
    }
}
