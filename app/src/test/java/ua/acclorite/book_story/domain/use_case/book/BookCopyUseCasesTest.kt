/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book

/**
 * Storing a book's file in the app's own directory, and giving it back.
 *
 * Robolectric only for `android.util.Log`, which the use cases write to.
 */
@RunWith(RobolectricTestRunner::class)
class BookCopyUseCasesTest {

    private val cloudBook = Book.default.copy(
        id = 7,
        title = "A book",
        author = UIText.StringValue("An author"),
        filePath = "/storage/a-book.fb2",
        documentAuthority = "com.google.android.apps.docs.storage",
        documentId = "acc=1;doc=encoded=abc"
    )

    private val copiedBook = cloudBook.copy(
        filePath = "/data/user/0/app/files/owned_books/7/a-book.fb2",
        documentAuthority = null,
        documentId = null,
        originAuthority = "com.google.android.apps.docs.storage",
        originDocumentId = "acc=1;doc=encoded=abc",
        originPath = "/storage/a-book.fb2"
    )

    @Test
    fun `storing moves the identity to the origin`() = runBlocking {
        val repository = FakeBookRepository(
            books = listOf(cloudBook),
            storedPath = "/data/user/0/app/files/owned_books/7/a-book.fb2"
        )

        val stored = StoreBookCopyUseCase(repository)(cloudBook)

        assertEquals("/data/user/0/app/files/owned_books/7/a-book.fb2", stored?.filePath)
        assertNull(stored?.documentAuthority)
        assertNull(stored?.documentId)
        assertEquals("com.google.android.apps.docs.storage", stored?.originAuthority)
        assertEquals("acc=1;doc=encoded=abc", stored?.originDocumentId)
        assertEquals("/storage/a-book.fb2", stored?.originPath)
        assertTrue(stored?.isOwnCopy == true)
    }

    @Test
    fun `storing writes the row as it stands, not as the caller remembers it`() = runBlocking {
        // The caller's copy is stale in the way that cost a cover: it predates
        // the insert that gave the book one.
        val repository = FakeBookRepository(
            books = listOf(cloudBook.copy(description = "read back from the row")),
            storedPath = "/copy/a-book.fb2"
        )

        StoreBookCopyUseCase(repository)(cloudBook.copy(description = null))

        assertEquals("read back from the row", repository.updated?.description)
    }

    @Test
    fun `a book that could not be copied is left alone`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(cloudBook), storedPath = null)

        assertNull(StoreBookCopyUseCase(repository)(cloudBook))
        assertNull(repository.updated)
    }

    @Test
    fun `a book that is already a copy is not copied again`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(copiedBook), storedPath = "/other")

        val stored = StoreBookCopyUseCase(repository)(copiedBook)

        assertEquals(copiedBook.filePath, stored?.filePath)
        assertNull(repository.storedFor)
    }

    @Test
    fun `releasing points the book back at what it was copied from`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(copiedBook))
        repository.releasedPath = "/storage/a-book.fb2"

        val released = ReleaseBookCopyUseCase(repository)(copiedBook)

        assertEquals("/storage/a-book.fb2", released?.filePath)
        assertEquals("com.google.android.apps.docs.storage", released?.documentAuthority)
        assertEquals("acc=1;doc=encoded=abc", released?.documentId)
        assertNull(released?.originPath)
        assertTrue(released?.isOwnCopy == false)
    }

    @Test
    fun `a copy whose original will not answer is kept`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(copiedBook))
        repository.releasedPath = null

        assertNull(ReleaseBookCopyUseCase(repository)(copiedBook))
        assertNull(repository.updated)
    }

    @Test
    fun `releasing a book that is not a copy does nothing`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(cloudBook))

        val released = ReleaseBookCopyUseCase(repository)(cloudBook)

        assertEquals(cloudBook.filePath, released?.filePath)
        assertNull(repository.releasedFor)
    }
}
