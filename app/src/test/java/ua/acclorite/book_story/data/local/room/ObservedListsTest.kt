/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.local.dto.BookEntity
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity
import ua.acclorite.book_story.data.mapper.book.BookMapperImpl
import ua.acclorite.book_story.data.repository.HistoryRepositoryImpl

/**
 * The Library and History lists are whatever these flows emit, with no refresh
 * calls after a write: a write that does not re-emit leaves a list stale.
 */
@RunWith(RobolectricTestRunner::class)
class ObservedListsTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = database.close()

    private suspend fun insertBook(title: String, inLibrary: Boolean = true): Int =
        database.bookDao.insertBook(
            BookEntity(
                title = title,
                author = "An author",
                description = null,
                filePath = "/storage/$title.fb2",
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                categories = emptyList(),
                inLibrary = inLibrary
            )
        ).toInt()

    /** Collects [flow] in the background; each `receive()` is the next emission. */
    private fun <T> kotlinx.coroutines.CoroutineScope.emissions(flow: Flow<T>): Channel<T> {
        val channel = Channel<T>(Channel.UNLIMITED)
        launch(Dispatchers.IO) { flow.collect { channel.send(it) } }
        return channel
    }

    private suspend fun <T> Channel<T>.next(): T = withTimeout(5_000) { receive() }

    /** One finished reading session of [bookId], as the reader records it. */
    private suspend fun read(bookId: Int, start: Long, end: Long) =
        database.statisticsDao.recordSession(
            session = ReadingSessionEntity(
                bookId = bookId,
                startTime = start,
                endTime = end,
                wordsRead = 100
            ),
            timeMs = end - start,
            coveragePercent = 0.1f,
            reachedEnd = false,
            title = "t",
            author = "a"
        )

    @Test
    fun `a library row carries when it was last read, and previews are left out`() = runBlocking {
        val read = insertBook("read")
        val never = insertBook("never")
        val marked = insertBook("marked")
        insertBook("preview", inLibrary = false)
        read(read, start = 1, end = 5)
        read(read, start = 6, end = 9)
        // A record with no sessions: marked finished by hand, never read.
        database.statisticsDao.setBookFinished(
            bookId = marked, title = "marked", author = "a", finished = true, now = 7
        )

        val rows = database.bookDao.observeLibraryBooks().first()

        assertEquals(
            mapOf(read to 9L, never to null, marked to null),
            rows.associate { it.book.id to it.lastOpened }
        )
    }

    @Test
    fun `the library re-emits after a book is added and after a reading session`() = runBlocking {
        val library = emissions(database.bookDao.observeLibraryBooks())
        assertEquals(emptyList<Int>(), library.next().map { it.book.id })

        val id = insertBook("added")
        assertEquals(listOf(id), library.next().map { it.book.id })

        read(id, start = 3, end = 7)
        assertEquals(7L, library.next().single().lastOpened)

        coroutineContext.cancelChildren()
    }

    @Test
    fun `the history re-emits after a book it shows is renamed`() = runBlocking {
        val id = insertBook("old")
        read(id, start = 1, end = 2)
        val history = emissions(database.statisticsDao.observeSessionsWithBook())
        assertEquals("old", history.next().single().book?.title)

        database.bookDao.updateTitle(id, "new")
        assertEquals("new", history.next().single().book?.title)

        coroutineContext.cancelChildren()
    }

    @Test
    fun `a deleted book's sessions leave the history`() = runBlocking {
        val kept = insertBook("kept")
        val deleted = insertBook("deleted")
        read(kept, start = 1, end = 2)
        read(deleted, start = 3, end = 4)
        database.statisticsDao.anonymiseBookSessions(deleted)

        val history = HistoryRepositoryImpl(database, BookMapperImpl()).observeHistory().first()

        assertEquals(listOf(kept), history.map { it.book.id })
    }

    @Test
    fun `the statistics tables re-emit after a session is recorded`() = runBlocking {
        val changes = emissions(
            database.invalidationTracker.createFlow("ReadingSessionEntity", "ReadBookEntity")
        )
        changes.next() // the initial state

        database.statisticsDao.insertSession(
            ReadingSessionEntity(bookId = null, startTime = 0, endTime = 60_000, wordsRead = 100)
        )
        changes.next()

        coroutineContext.cancelChildren()
    }
}
