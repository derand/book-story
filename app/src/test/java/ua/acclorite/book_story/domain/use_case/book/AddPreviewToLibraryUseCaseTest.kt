/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.service.FileProvider

/**
 * Keeping a previewed book. The point of the use case is that a book is never
 * added in a state where it could not be opened again: the grant that reaches
 * its file has to exist *before* the row is promoted, because the grant the file
 * manager handed over dies with the task.
 */
@RunWith(RobolectricTestRunner::class)
class AddPreviewToLibraryUseCaseTest {

    private val preview = Book.default.copy(
        id = 7,
        filePath = "/storage/emulated/0/Download/Solaris.fb2",
        inLibrary = false,
        previewUri = "content://provider/document/7"
    )

    private class FakeFileProvider(private val reachable: Boolean) : FileProvider {
        override fun getFileFromBook(book: Book): Result<CachedFile> =
            if (reachable) {
                Result.success(
                    CachedFile(
                        context = RuntimeEnvironment.getApplication(),
                        uri = Uri.parse("content://test/book")
                    )
                )
            } else {
                Result.failure(NoSuchElementException("No grant reaches it."))
            }

        override fun getStorageFiles(): Result<List<CachedFile>> = Result.success(emptyList())

        override fun getStorageSources(): Result<List<BookSource>> = Result.success(emptyList())
    }

    @Test
    fun `a reachable book is added`() = runBlocking {
        val repository = FakeBookRepository()
        val useCase = AddPreviewToLibraryUseCase(repository, FakeFileProvider(reachable = true))

        val result = useCase(preview)

        assertTrue(result is AddPreviewToLibraryUseCase.Result.Added)
        assertEquals(true, repository.updated?.inLibrary)
        assertEquals(7, repository.updated?.id)
        // What was written comes back, so the reader can adopt it: it writes the
        // whole book row on every settled scroll, and a stale copy would undo
        // the promotion at the next one.
        assertEquals(repository.updated, (result as AddPreviewToLibraryUseCase.Result.Added).book)
        assertNull(result.book.previewUri)
    }

    @Test
    fun `an unreachable book asks for its folder and is not added`() = runBlocking {
        val repository = FakeBookRepository()
        val useCase = AddPreviewToLibraryUseCase(repository, FakeFileProvider(reachable = false))

        val result = useCase(preview)

        assertEquals(
            AddPreviewToLibraryUseCase.Result.NeedsGrant("/storage/emulated/0/Download"),
            result
        )
        // Nothing was written: a row promoted here could never be opened again.
        assertNull(repository.updated)
    }

    @Test
    fun `a book with no path is kept by keeping its file`() = runBlocking {
        val repository = FakeBookRepository(storedPath = "/data/books/7/Solaris.fb2")
        val useCase = AddPreviewToLibraryUseCase(repository, FakeFileProvider(reachable = true))

        val result = useCase(preview.copy(filePath = ""))

        assertTrue(result is AddPreviewToLibraryUseCase.Result.Added)
        assertEquals(repository.updated, (result as AddPreviewToLibraryUseCase.Result.Added).book)
        // The copy becomes the book's location, and the transient URI goes.
        assertEquals("/data/books/7/Solaris.fb2", repository.updated?.filePath)
        assertEquals(true, repository.updated?.inLibrary)
        assertNull(repository.updated?.previewUri)
        // No grant was asked for: nothing about the copy needs one.
        assertEquals(7, repository.storedFor?.id)
    }

    @Test
    fun `a book whose file cannot be stored is not added`() = runBlocking {
        val repository = FakeBookRepository(storedPath = null)
        val useCase = AddPreviewToLibraryUseCase(repository, FakeFileProvider(reachable = true))

        val result = useCase(preview.copy(filePath = ""))

        assertEquals(AddPreviewToLibraryUseCase.Result.Failed, result)
        assertNull(repository.updated)
    }

    @Test
    fun `a file at the storage root still names a folder`() = runBlocking {
        val repository = FakeBookRepository()
        val useCase = AddPreviewToLibraryUseCase(repository, FakeFileProvider(reachable = false))

        val result = useCase(preview.copy(filePath = "/Solaris.fb2"))

        assertTrue(result is AddPreviewToLibraryUseCase.Result.NeedsGrant)
        // No folder above it that can be named, so the picker opens wherever it
        // likes rather than being sent to "".
        assertNull((result as AddPreviewToLibraryUseCase.Result.NeedsGrant).folder)
    }
}
