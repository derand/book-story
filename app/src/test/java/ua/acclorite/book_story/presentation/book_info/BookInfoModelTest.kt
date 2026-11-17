/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.book_info

import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.history.History
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.domain.model.statistics.ReadBook
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import ua.acclorite.book_story.domain.repository.HistoryRepository
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import ua.acclorite.book_story.domain.use_case.book.CanResetCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.book.DeleteBookUseCase
import ua.acclorite.book_story.domain.use_case.book.FakeBookRepository
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import ua.acclorite.book_story.domain.use_case.book.GetFileFromBookUseCase
import ua.acclorite.book_story.domain.use_case.book.RefreshBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.ReleaseBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.ResetCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.book.StoreBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateBookUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.GetBookStatisticsUseCase
import ua.acclorite.book_story.domain.use_case.statistics.SetBookFinishedUseCase
import java.io.File

/**
 * Book info's writes against leaving the screen. The model is one per activity,
 * so "leaving" is [BookInfoModel.leave] and opening the next book is another
 * [BookInfoModel.init] on the same model.
 *
 * Robolectric for the main looper `viewModelScope` runs on; the model hops to
 * `Default`, so the tests wait for what they expect rather than for one idle.
 */
@RunWith(RobolectricTestRunner::class)
class BookInfoModelTest {

    private val first = Book.default.copy(
        id = 1,
        title = "First",
        author = UIText.StringValue("An author"),
        filePath = "/storage/first.fb2",
        documentAuthority = "com.google.android.apps.docs.storage",
        documentId = "doc-1"
    )
    private val second = first.copy(id = 2, title = "Second", filePath = "/storage/second.fb2")

    private val repository = FakeBookRepository(
        books = listOf(first, second),
        storedPath = "/copy/first.fb2"
    )
    private val model = model(repository)

    private val effects = mutableListOf<BookInfoEffect>()
    private val collector = CoroutineScope(Dispatchers.Main).apply {
        launch { model.effects.collect { effects += it } }
    }

    @After
    fun stopCollecting() = collector.cancel()

    @Test
    fun `an edit followed at once by Back is still written`() {
        val gate = gateTitleWrite()
        open(first)

        model.onEvent(BookInfoEvent.OnActionTitleDialog("Renamed"))
        waitUntil("the write started") { gate.entered.isCompleted }
        model.leave()
        gate.release.complete(Unit)

        waitUntil("the title written") { repository.titleWrites.isNotEmpty() }
        assertEquals(listOf("1 Renamed"), repository.titleWrites)
    }

    @Test
    fun `a write that ends under the next book leaves that book alone`() {
        val gate = gateTitleWrite()
        open(first)

        model.onEvent(BookInfoEvent.OnActionTitleDialog("Renamed"))
        waitUntil("the write started") { gate.entered.isCompleted }
        model.leave()
        open(second)
        gate.release.complete(Unit)

        waitUntil("the title written") { repository.titleWrites.isNotEmpty() }
        idle()
        assertEquals("Second", model.state.value.book.title)
        assertFalse(BookInfoEffect.OnTitleChanged in effects)
    }

    @Test
    fun `a delete that ends under the next book does not close it`() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.onDeleteRecords = {
            entered.complete(Unit)
            release.await()
        }
        open(first)

        model.onEvent(BookInfoEvent.OnActionDeleteDialog)
        waitUntil("the delete started") { entered.isCompleted }
        model.leave()
        open(second)
        release.complete(Unit)

        waitUntil("the delete done") { "images 1" in repository.deleteCalls }
        idle()
        assertEquals(2, model.state.value.book.id)
        assertFalse(BookInfoEffect.OnNavigateBack in effects)
        assertFalse(BookInfoEffect.OnBookDeleted in effects)
    }

    @Test
    fun `a second tap on the copy switch starts no second copy`() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repository.onStore = {
            entered.complete(Unit)
            release.await()
        }
        open(first)

        // Both before anything ran: the same frame.
        model.onEvent(BookInfoEvent.OnToggleLocalCopy)
        model.onEvent(BookInfoEvent.OnToggleLocalCopy)
        waitUntil("the copy started") { entered.isCompleted }

        // Leaving and coming back while it runs: the switch is still off.
        model.leave()
        open(first)
        assertTrue(model.state.value.changingLocalCopy)
        model.onEvent(BookInfoEvent.OnToggleLocalCopy)

        release.complete(Unit)
        waitUntil("the copy done") { !model.state.value.changingLocalCopy }
        assertEquals(1, repository.copyCalls.count { it == "store" })
        assertTrue(model.state.value.book.isOwnCopy)
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private fun gateTitleWrite(): Gate = Gate().also { gate ->
        repository.onUpdateTitle = {
            gate.entered.complete(Unit)
            gate.release.await()
        }
    }

    private fun open(book: Book) {
        model.init(bookId = book.id, changePath = false)
        waitUntil("book ${book.id} shown") {
            model.state.value.book.id == book.id && !model.state.value.loadingFile
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for: $what" }
            Thread.sleep(5)
        }
    }

    private fun model(repository: FakeBookRepository): BookInfoModel {
        val covers = NoCovers()
        val statistics = NoStatistics()
        return BookInfoModel(
            updateCoverImageUseCase = UpdateCoverImageUseCase(repository, covers),
            updateBookUseCase = UpdateBookUseCase(repository),
            getBookUseCase = GetBookUseCase(repository, NoHistory()),
            getFileFromBookUseCase = GetFileFromBookUseCase(repository),
            deleteBookUseCase = DeleteBookUseCase(repository, covers),
            canResetCoverImageUseCase = CanResetCoverImageUseCase(
                RuntimeEnvironment.getApplication(), repository, covers
            ),
            resetCoverImageUseCase = ResetCoverImageUseCase(repository, covers),
            getBookStatisticsUseCase = GetBookStatisticsUseCase(statistics),
            setBookFinishedUseCase = SetBookFinishedUseCase(statistics),
            storeBookCopyUseCase = StoreBookCopyUseCase(repository),
            releaseBookCopyUseCase = ReleaseBookCopyUseCase(repository),
            refreshBookCopyUseCase = RefreshBookCopyUseCase(repository)
        )
    }

    private class NoHistory : HistoryRepository {
        override suspend fun getHistoryForBook(bookId: Int): Result<History> =
            Result.failure(NoSuchElementException("No history."))

        override suspend fun addHistory(history: History): Result<Unit> = unused()
        override fun observeHistory(): Flow<List<History>> = unused()
        override suspend fun deleteWholeHistory(): Result<Unit> = unused()
        override suspend fun deleteHistory(history: History): Result<Unit> = unused()
    }

    /** A book never read: no record, no coverage. */
    private class NoStatistics : StatisticsRepository {
        override suspend fun getReadBook(bookId: Int): Result<ReadBook?> = Result.success(null)
        override suspend fun getCoverage(bookId: Int): Result<ReadingCoverage?> =
            Result.success(null)

        override suspend fun recordSession(
            session: ReadingSession,
            title: String,
            author: String,
            coveragePercent: Float,
            reachedEnd: Boolean
        ): Result<Unit> = unused()

        override suspend fun saveCoverage(coverage: ReadingCoverage): Result<Unit> = unused()

        override suspend fun setFinished(
            bookId: Int,
            title: String,
            author: String,
            finished: Boolean
        ): Result<Unit> = unused()

        override suspend fun getTypicalWordsPerMinute(bookId: Int?): Result<Int?> = unused()
        override suspend fun countActiveDays(bookId: Int): Result<Int> = unused()
        override suspend fun getLibraryStatistics(): Result<LibraryStatistics> = unused()
        override fun observeLibraryStatistics(): Flow<Result<LibraryStatistics>> = unused()
        override suspend fun deleteAllStatistics(): Result<Unit> = unused()
    }

    private class NoCovers : CoverImageHandler {
        override suspend fun decodeCover(uri: Uri): Result<CoverImage> = unused()
        override suspend fun saveCover(coverImage: CoverImage): Result<File> = unused()
        override suspend fun deleteCover(coverImage: Uri): Result<Unit> = unused()
        override suspend fun compressCover(coverImage: CoverImage): Result<CoverImage> = unused()
    }

    private companion object {
        fun unused(): Nothing = throw UnsupportedOperationException("Not part of this fake.")
    }
}
