/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files Browse must not offer, because the library already holds them.
 * Too loose and a book the user does not have is unreachable; too strict and
 * the library fills with copies of one book, which is what a copy's silence
 * about its origin used to cause.
 */
class LibraryFilesTest {

    private val drive = "com.google.android.apps.docs.storage"
    private val device = "com.android.externalstorage.documents"

    private fun inPlace(
        filePath: String,
        documentAuthority: String? = null,
        documentId: String? = null
    ) = Book.default.copy(
        filePath = filePath,
        documentAuthority = documentAuthority,
        documentId = documentId
    )

    private fun copyOf(
        originPath: String,
        originAuthority: String? = null,
        originDocumentId: String? = null
    ) = Book.default.copy(
        filePath = "/data/user/0/app/files/owned_books/7/book.epub",
        originPath = originPath,
        originAuthority = originAuthority,
        originDocumentId = originDocumentId
    )

    @Test
    fun `a book is recognised by its identity`() {
        val files = listOf(inPlace("/storage/audit/atb.epub", drive, "doc=1")).libraryFiles()

        assertTrue(files.holds("/storage/audit/atb.epub", drive, "doc=1"))
    }

    @Test
    fun `a copy is recognised by the identity it was taken from`() {
        val files = listOf(copyOf("/storage/audit/ymusic.epub", drive, "doc=2")).libraryFiles()

        assertTrue(files.holds("/storage/audit/ymusic.epub", drive, "doc=2"))
    }

    @Test
    fun `a copy is recognised by its origin path when the origin had no identity`() {
        val files = listOf(copyOf("/storage/emulated/0/Books/Solaris.fb2")).libraryFiles()

        assertTrue(files.holds("/storage/emulated/0/Books/Solaris.fb2", null, null))
    }

    @Test
    fun `a copy does not claim a file by its own path`() {
        val files = listOf(copyOf("/storage/audit/ymusic.epub", drive, "doc=2")).libraryFiles()

        assertFalse(files.holds("/data/user/0/app/files/owned_books/7/book.epub", null, null))
    }

    @Test
    fun `another document from the same provider is still offered`() {
        val files = listOf(copyOf("/storage/audit/ymusic.epub", drive, "doc=2")).libraryFiles()

        // The path is the one the provider invents for every file in that tree,
        // so only the id may answer here, and it says these are two documents.
        assertFalse(files.holds("/storage/audit/silpo.epub", drive, "doc=3"))
    }

    @Test
    fun `a path answers for a book that has no identity yet`() {
        val files = listOf(inPlace("/storage/emulated/0/Books/Solaris.fb2")).libraryFiles()

        assertTrue(files.holds("/storage/emulated/0/books/solaris.fb2", null, null))
    }

    @Test
    fun `a path does not answer for a book known by its identity`() {
        val files = listOf(inPlace("/storage/audit/atb.epub", drive, "doc=1")).libraryFiles()

        // Same invented path, another document: the identity had its say above.
        assertFalse(files.holds("/storage/audit/atb.epub", drive, "doc=9"))
    }

    @Test
    fun `a file the library does not hold is offered`() {
        val files = listOf(
            inPlace("/storage/emulated/0/Books/Solaris.fb2", device, "primary:Books/Solaris.fb2"),
            copyOf("/storage/audit/ymusic.epub", drive, "doc=2")
        ).libraryFiles()

        assertFalse(files.holds("/storage/emulated/0/Books/Nebula.epub", device, "primary:x"))
    }

    @Test
    fun `an empty library holds nothing`() {
        val files = emptyList<Book>().libraryFiles()

        assertFalse(files.holds("/storage/audit/atb.epub", drive, "doc=1"))
    }
}
