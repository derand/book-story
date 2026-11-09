/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.service.CoverImageHandler
import java.io.File

/**
 * Deleting a book: the database in one step, the files only after it.
 *
 * Robolectric for `android.util.Log` and `Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class DeleteBookUseCaseTest {

    private val storedCover = Uri.parse("file:///data/user/0/app/files/covers/new.webp")

    private val book = Book.default.copy(
        id = 7,
        // What a screen loaded before the cover was changed.
        coverImage = Uri.parse("file:///data/user/0/app/files/covers/old.webp")
    )

    private val repository = FakeBookRepository(books = listOf(book))
    private val useCase = DeleteBookUseCase(repository, RecordingCovers(repository.deleteCalls))

    @Test
    fun `the files go only after the records, and the cover is the stored one`() = runBlocking {
        repository.deleteRecordsResult = Result.success(storedCover)

        useCase(book)

        assertEquals(
            listOf("records 7", "cover $storedCover", "file 7", "images 7"),
            repository.deleteCalls
        )
    }

    @Test
    fun `a failed records delete leaves every file in place`() = runBlocking {
        repository.deleteRecordsResult = Result.failure(IllegalStateException("disk full"))

        useCase(book)

        assertEquals(listOf("records 7"), repository.deleteCalls)
    }

    @Test
    fun `leaving the screen mid-delete does not stop the file deletes`() = runBlocking {
        repository.deleteRecordsResult = Result.success(storedCover)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        repository.onDeleteRecords = {
            started.complete(Unit)
            gate.await()
        }

        val job = launch { useCase(book) }
        started.await()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertEquals(
            listOf("records 7", "cover $storedCover", "file 7", "images 7"),
            repository.deleteCalls
        )
    }

    /** Logs cover deletes into the repository's log, and suspends in them. */
    private class RecordingCovers(private val calls: MutableList<String>) : CoverImageHandler {

        override suspend fun deleteCover(coverImage: Uri): Result<Unit> {
            // A suspension point, where a cancelled caller would stop.
            yield()
            calls += "cover $coverImage"
            return Result.success(Unit)
        }

        override suspend fun decodeCover(uri: Uri): Result<CoverImage> = unused()
        override suspend fun saveCover(coverImage: CoverImage): Result<File> = unused()
        override suspend fun compressCover(coverImage: CoverImage): Result<CoverImage> = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }
}
