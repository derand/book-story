/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.local.dto.BookEntity
import ua.acclorite.book_story.data.local.dto.HistoryEntity
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.mapper.book.BookMapperImpl
import ua.acclorite.book_story.data.mapper.history.HistoryMapperImpl

/**
 * A history entry whose book is gone costs that entry, not the History screen:
 * nothing ties `HistoryEntity.bookId` to a row, so such entries exist.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryOrphanTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val repository = HistoryRepositoryImpl(database, HistoryMapperImpl(BookMapperImpl()))

    @After
    fun tearDown() = database.close()

    private suspend fun insertBook(): Int = database.bookDao.insertBook(
        BookEntity(
            title = "A book",
            author = "An author",
            description = null,
            filePath = "/storage/a-book.fb2",
            scrollIndex = 0,
            scrollOffset = 0,
            progress = 0f,
            categories = emptyList()
        )
    ).toInt()

    @Test
    fun `an orphan entry is dropped and the rest of the history stays`() = runBlocking {
        val bookId = insertBook()
        database.historyDao.insertHistory(HistoryEntity(bookId = bookId, time = 1))
        database.historyDao.insertHistory(HistoryEntity(bookId = bookId + 100, time = 2))

        val history = repository.getHistory().getOrThrow()

        assertEquals(listOf(bookId), history.map { it.book.id })
    }

    @Test
    fun `an orphan entry is no history for its book`() = runBlocking {
        database.historyDao.insertHistory(HistoryEntity(bookId = 42, time = 1))

        val result = repository.getHistoryForBook(42)

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
}
