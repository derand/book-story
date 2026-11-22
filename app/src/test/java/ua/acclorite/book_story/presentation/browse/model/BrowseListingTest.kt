/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse.model

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.libraryFiles

/** What Browse lists out of a walk, now that the walk no longer narrows it. */
class BrowseListingTest {

    private fun file(name: String, size: Long = 1, documentId: String? = null) = File(
        name = name,
        uri = "content://test/document/$name",
        path = "/storage/Books/$name",
        size = size,
        lastModified = 0,
        isDirectory = false,
        documentAuthority = documentId?.let { "test" },
        documentId = documentId
    )

    private val nothing = emptyList<Book>().libraryFiles()

    private fun List<File>.names(
        existing: List<Book> = emptyList(),
        options: BrowseListOptions = BrowseListOptions()
    ) = browseListing(existing.libraryFiles(), options).map { it.name }

    @Test
    fun `a search matches the name, trimmed and in any case`() {
        val files = listOf(file("War and Peace.fb2"), file("Anna.epub"))

        assertEquals(
            listOf("War and Peace.fb2"),
            files.names(options = BrowseListOptions(query = "  PEACE "))
        )
        assertEquals(2, files.browseListing(nothing, BrowseListOptions(query = "   ")).size)
    }

    @Test
    fun `a book the library holds is not offered, by identity or by path`() {
        val byId = file("Drive.epub", documentId = "doc-7")
        val byPath = file("Local.fb2")
        val other = file("Other.fb2")
        val library = listOf(
            // Moved since, so only the identity still answers for it.
            Book.default.copy(id = 1, filePath = "/elsewhere", documentAuthority = "test", documentId = "doc-7"),
            Book.default.copy(id = 2, filePath = byPath.path.uppercase())
        )

        assertEquals(listOf("Other.fb2"), listOf(byId, byPath, other).names(existing = library))
    }

    @Test
    fun `the extension filter and the sort apply together`() {
        val files = listOf(
            file("b.fb2", size = 3),
            file("a.epub", size = 2),
            file("c.FB2", size = 1)
        )

        assertEquals(
            listOf("c.FB2", "b.fb2"),
            files.names(
                options = BrowseListOptions(
                    includedExtensions = listOf(".fb2"),
                    sortOrder = BrowseSortOrder.FILE_SIZE,
                    descending = false
                )
            )
        )
    }
}
