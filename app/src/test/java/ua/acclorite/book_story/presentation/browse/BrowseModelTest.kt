/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse

import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.data.local.data_store.DataStore
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.FileSystemRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import ua.acclorite.book_story.domain.service.FileProvider
import ua.acclorite.book_story.domain.use_case.book.AddBookUseCase
import ua.acclorite.book_story.domain.use_case.book.FakeBookRepository
import ua.acclorite.book_story.domain.use_case.book.StoreBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetBookFromFileUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetFilesUseCase
import ua.acclorite.book_story.domain.use_case.file_system.ObserveLibraryFilesUseCase
import ua.acclorite.book_story.presentation.browse.model.BrowseSortOrder
import java.time.Duration

/**
 * When Browse walks the granted folders. A walk is a query per directory, over
 * the network for a cloud folder, so it is due only on the first visit, on
 * pull-to-refresh and when the folders change — never for a search, a sort, a
 * filter or a change to the library.
 *
 * "On screen" is a collector of [BrowseModel.state], as `BrowseScreen` is.
 */
@RunWith(RobolectricTestRunner::class)
class BrowseModelTest {

    private val war = file("War.fb2", lastModified = 1)
    private val peace = file("Peace.epub", lastModified = 2)
    private val notes = file("Notes.txt", lastModified = 3)

    private val files = FakeFiles(listOf(war, peace, notes))
    private val library = MutableStateFlow(emptyList<Book>())
    private val settings = SettingsManager(NoStore())
    private val model = model()

    private val screen = CoroutineScope(Dispatchers.Main)
    private var shown: Job? = null

    @After
    fun tearDown() {
        screen.cancel()
        // The channel is static: a model left listening takes the next
        // test's signal.
        model.viewModelScope.cancel()
        BrowseScreen.refreshListChannel.tryReceive()
    }

    @Test
    fun `nothing is walked until the list is shown, then once`() {
        settle()
        assertEquals(0, files.walks)

        show()
        waitUntil("the listing") { names() == listOf("Notes.txt", "Peace.epub", "War.fb2") }
        assertEquals(1, files.walks)
    }

    @Test
    fun `coming back to the tab shows the last walk`() {
        show()
        waitUntil("the walk over") { names().size == 3 && !model.state.value.isRefreshing }

        hide()
        show()
        settle()
        assertEquals(3, names().size)
        assertEquals(1, files.walks)
    }

    @Test
    fun `a search, a sort and a filter narrow the last walk`() {
        show()
        waitUntil("the listing") { names().size == 3 }

        model.onEvent(BrowseEvent.OnSearchVisibility(show = true))
        model.onEvent(BrowseEvent.OnSearchQueryChange("a"))
        model.onEvent(BrowseEvent.OnSearch)
        waitUntil("the search") { names() == listOf("Peace.epub", "War.fb2") }

        settings.browseSortOrder.update(BrowseSortOrder.NAME)
        settings.browseSortOrderDescending.update(false)
        waitUntil("the sort") { names() == listOf("Peace.epub", "War.fb2") }
        settings.browseSortOrderDescending.update(true)
        waitUntil("the order flipped") { names() == listOf("War.fb2", "Peace.epub") }

        settings.browseIncludedFilterItems.update(listOf(".epub"))
        waitUntil("the filter") { names() == listOf("Peace.epub") }

        settle()
        assertEquals(1, files.walks)
    }

    @Test
    fun `a book added leaves the list and a book deleted comes back, without a walk`() {
        show()
        waitUntil("the listing") { names().size == 3 }

        library.value = listOf(Book.default.copy(id = 1, filePath = war.path))
        waitUntil("the added book gone") { names() == listOf("Notes.txt", "Peace.epub") }

        library.value = emptyList()
        waitUntil("the deleted book back") { names().size == 3 }

        settle()
        assertEquals(1, files.walks)
    }

    @Test
    fun `a selection survives a library change`() {
        show()
        waitUntil("the listing") { names().size == 3 }
        model.onEvent(BrowseEvent.OnToggleFile(peace.uri))
        idle()

        library.value = listOf(Book.default.copy(id = 1, filePath = war.path))
        waitUntil("the added book gone") { names().size == 2 }

        val selected = model.state.value.files.filter { it.selected }.map { it.name }
        assertEquals(listOf("Peace.epub"), selected)
    }

    @Test
    fun `pull-to-refresh walks again`() {
        show()
        waitUntil("the listing") { names().size == 3 }

        files.files = listOf(war)
        model.onEvent(BrowseEvent.OnRefreshList(loading = false, hideSearch = true))
        waitUntil("the new walk") { names() == listOf("War.fb2") }
        assertEquals(2, files.walks)
    }

    @Test
    fun `changed folders walk now on screen, and on the next visit off it`() {
        show()
        waitUntil("the listing") { names().size == 3 }

        files.files = listOf(war, peace)
        BrowseScreen.refreshListChannel.trySend(Unit)
        waitUntil("the walk on screen") { names().size == 2 && !model.state.value.isRefreshing }
        assertEquals(2, files.walks)

        hide()
        files.files = listOf(war)
        BrowseScreen.refreshListChannel.trySend(Unit)
        settle()
        assertEquals(2, files.walks)

        show()
        waitUntil("the walk on the next visit") { names() == listOf("War.fb2") }
        assertEquals(3, files.walks)
    }

    private fun names() = model.state.value.files.map { it.name }

    private fun show() {
        shown = screen.launch { model.state.collect {} }
        idle()
    }

    private fun hide() {
        shown?.cancel()
        idle()
    }

    /** Runs the main looper, its clock moved on: the refresh indicator waits on a delay. */
    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))

    /** Lets whatever was going to start start: for asserting that nothing did. */
    private fun settle() {
        val end = System.currentTimeMillis() + 200
        while (System.currentTimeMillis() < end) {
            idle()
            Thread.sleep(5)
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) {
                "Timed out waiting for: $what (shown: ${names()})"
            }
            Thread.sleep(5)
        }
    }

    private fun model(): BrowseModel {
        val books = FakeBookRepository(library = library)
        return BrowseModel(
            addBookUseCase = AddBookUseCase(books, NoCovers),
            storeBookCopyUseCase = StoreBookCopyUseCase(books),
            getFilesUseCase = GetFilesUseCase(files),
            getBookFromFileUseCase = GetBookFromFileUseCase(files),
            getBookSourcesUseCase = GetBookSourcesUseCase(NoFolders),
            observeLibraryFilesUseCase = ObserveLibraryFilesUseCase(books),
            settings = settings
        )
    }

    private fun file(name: String, lastModified: Long) = File(
        name = name,
        uri = "content://test/document/$name",
        path = "/storage/Books/$name",
        size = 1,
        lastModified = lastModified,
        isDirectory = false
    )

    /** Granted folders holding [files]; counts the walks over them. */
    private class FakeFiles(var files: List<File>) : FileSystemRepository {
        @Volatile
        var walks = 0
            private set

        override suspend fun listBookFiles(): Result<List<File>> {
            walks++
            return Result.success(files)
        }

        override suspend fun getBookFromFile(file: File): Result<Pair<Book, CoverImage?>> =
            error("not asked")

        override suspend fun getFileFromUri(uri: String): Result<File> = error("not asked")
    }

    private object NoFolders : FileProvider {
        override suspend fun getFileFromBook(book: Book): Result<CachedFile> = error("not asked")
        override suspend fun getStorageFiles(): Result<List<CachedFile>> = error("not asked")
        override suspend fun getStorageSources(): Result<List<BookSource>> = Result.success(emptyList())
    }

    private object NoCovers : CoverImageHandler {
        override suspend fun decodeCover(uri: Uri): Result<CoverImage> = error("not asked")
        override suspend fun saveCover(coverImage: CoverImage) = error("not asked")
        override suspend fun deleteCover(coverImage: Uri) = error("not asked")
        override suspend fun isSavedAs(stored: Uri, coverImage: CoverImage) = error("not asked")
    }

    private class NoStore : DataStore {
        override suspend fun <T> getNullableData(key: Preferences.Key<T>): T? = null
        override suspend fun <T> putData(key: Preferences.Key<T>, value: T) {}
    }
}
