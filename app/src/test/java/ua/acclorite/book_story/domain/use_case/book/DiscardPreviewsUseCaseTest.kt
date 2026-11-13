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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.service.CoverImageHandler
import java.io.File

/**
 * Leaving a preview. The reader passes the book as it holds it, and a Keep may
 * have promoted the row since: the decision has to be the row's.
 */
@RunWith(RobolectricTestRunner::class)
class DiscardPreviewsUseCaseTest {

    private val preview = Book.default.copy(id = 7, inLibrary = false)

    private fun useCase(repository: FakeBookRepository) =
        DiscardPreviewsUseCase(repository, DeleteBookUseCase(repository, NoCovers))

    @Test
    fun `a preview is discarded`() = runBlocking {
        val repository = FakeBookRepository(previews = listOf(preview))

        useCase(repository).discard(preview)

        assertEquals(listOf("records 7", "file 7", "images 7"), repository.deleteCalls)
    }

    @Test
    fun `a book kept since the caller read it is not discarded`() = runBlocking {
        val repository = FakeBookRepository(books = listOf(preview.copy(inLibrary = true)))

        useCase(repository).discard(preview)

        assertTrue(repository.deleteCalls.isEmpty())
    }

    @Test
    fun `a preview already gone is left alone`() = runBlocking {
        val repository = FakeBookRepository()

        useCase(repository).discard(preview)

        assertTrue(repository.deleteCalls.isEmpty())
    }

    private object NoCovers : CoverImageHandler {
        override suspend fun deleteCover(coverImage: Uri): Result<Unit> = unused()
        override suspend fun decodeCover(uri: Uri): Result<CoverImage> = unused()
        override suspend fun saveCover(coverImage: CoverImage): Result<File> = unused()
        override suspend fun compressCover(coverImage: CoverImage): Result<CoverImage> = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }
}
