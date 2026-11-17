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
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.domain.model.statistics.ReadingSession

/**
 * A session and the book record it adds to are written together or not at all:
 * the record's totals are sums over the sessions table.
 */
@RunWith(RobolectricTestRunner::class)
class RecordSessionTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val repository = StatisticsRepositoryImpl(database)

    @After
    fun tearDown() = database.close()

    private fun session(start: Long, words: Int) = ReadingSession(
        bookId = 7,
        startTime = start,
        endTime = start + 60_000,
        wordsRead = words,
        overlayMs = 0
    )

    private suspend fun record(session: ReadingSession, reachedEnd: Boolean = false) =
        repository.recordSession(
            session = session,
            title = "A book",
            author = "An author",
            coveragePercent = 0.5f,
            reachedEnd = reachedEnd
        )

    private fun sessionCount(): Int =
        database.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM ReadingSessionEntity")
            .use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun `the first session stores itself and starts the record`() = runBlocking {
        assertTrue(record(session(start = 1_000, words = 300)).isSuccess)

        val book = repository.getReadBook(7).getOrThrow()!!
        assertEquals(1, sessionCount())
        assertEquals(1, book.sessions)
        assertEquals(300, book.totalWords)
        assertEquals(60_000L, book.totalTimeMs)
        assertEquals(1_000L, book.firstReadAt)
    }

    @Test
    fun `a later session adds to the record`() = runBlocking {
        record(session(start = 1_000, words = 300))
        record(session(start = 100_000, words = 200), reachedEnd = true)

        val book = repository.getReadBook(7).getOrThrow()!!
        assertEquals(2, sessionCount())
        assertEquals(2, book.sessions)
        assertEquals(500, book.totalWords)
        assertEquals(true, book.finished)
    }

    @Test
    fun `a session whose record cannot be written is not stored either`() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER no_records BEFORE INSERT ON ReadBookEntity
            BEGIN SELECT RAISE(ABORT, 'refused'); END
            """
        )

        assertTrue(record(session(start = 1_000, words = 300)).isFailure)

        assertEquals(0, sessionCount())
        assertEquals(null, repository.getReadBook(7).getOrThrow())
    }
}
