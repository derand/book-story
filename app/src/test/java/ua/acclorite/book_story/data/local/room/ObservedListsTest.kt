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
import ua.acclorite.book_story.data.local.dto.HistoryEntity
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity

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

    @Test
    fun `a library row carries its latest open, and previews are left out`() = runBlocking {
        val opened = insertBook("opened")
        val never = insertBook("never")
        insertBook("preview", inLibrary = false)
        database.historyDao.insertHistory(HistoryEntity(bookId = opened, time = 5))
        database.historyDao.insertHistory(HistoryEntity(bookId = opened, time = 9))

        val rows = database.bookDao.observeLibraryBooks().first()

        assertEquals(
            mapOf(opened to 9L, never to null),
            rows.associate { it.book.id to it.lastOpened }
        )
    }

    @Test
    fun `the library re-emits after a book is added and after an open`() = runBlocking {
        val library = emissions(database.bookDao.observeLibraryBooks())
        assertEquals(emptyList<Int>(), library.next().map { it.book.id })

        val id = insertBook("added")
        assertEquals(listOf(id), library.next().map { it.book.id })

        database.historyDao.insertHistory(HistoryEntity(bookId = id, time = 7))
        assertEquals(7L, library.next().single().lastOpened)

        coroutineContext.cancelChildren()
    }

    @Test
    fun `the history re-emits after a book it shows is renamed`() = runBlocking {
        val id = insertBook("old")
        database.historyDao.insertHistory(HistoryEntity(bookId = id, time = 1))
        val history = emissions(database.historyDao.observeHistoryWithBook())
        assertEquals("old", history.next().single().book?.title)

        database.bookDao.updateTitle(id, "new")
        assertEquals("new", history.next().single().book?.title)

        coroutineContext.cancelChildren()
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
