/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ua.acclorite.book_story.data.local.dto.ReadBookEntity
import ua.acclorite.book_story.data.local.dto.ReadingCoverageEntity
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity

@Dao
interface StatisticsDao {

    @Insert
    suspend fun insertSession(session: ReadingSessionEntity)

    /**
     * Unlinks a deleted book's sessions instead of deleting them. The per-book
     * detail stops matching `WHERE bookId = :bookId` and disappears, while the
     * day, the duration and the words stay in the lifetime totals — so deleting
     * a book cannot retroactively shorten a streak or shrink the total time.
     */
    @Query("UPDATE ReadingSessionEntity SET bookId = NULL WHERE bookId = :bookId")
    suspend fun anonymiseBookSessions(bookId: Int)

    @Query("SELECT * FROM readingcoverageentity WHERE bookId = :bookId")
    suspend fun getCoverage(bookId: Int): ReadingCoverageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCoverage(coverage: ReadingCoverageEntity)

    /** Unlike sessions, coverage goes: without the text its indices mean nothing. */
    @Query("DELETE FROM readingcoverageentity WHERE bookId = :bookId")
    suspend fun deleteCoverage(bookId: Int)

    @Query("SELECT * FROM readbookentity WHERE bookId = :bookId")
    suspend fun getReadBook(bookId: Int): ReadBookEntity?

    /**
     * Folds one finished session into the book's record, in SQL rather than by
     * reading the row and writing it back — the totals are accumulated where
     * they live.
     *
     * `finished` only ever goes up: [reachedEnd] says this session got to the
     * end of the book, and a later session that stops earlier must not take
     * that back. Returns 0 when the book has no record yet.
     */
    @Query(
        """
        UPDATE ReadBookEntity
        SET totalTimeMs = totalTimeMs + :timeMs,
            totalWords = totalWords + :words,
            sessions = sessions + 1,
            lastReadAt = :lastReadAt,
            coveragePercent = :coveragePercent,
            finished = MAX(finished, :reachedEnd),
            title = :title,
            author = :author
        WHERE bookId = :bookId
        """
    )
    suspend fun addSessionToReadBook(
        bookId: Int,
        timeMs: Long,
        words: Int,
        lastReadAt: Long,
        coveragePercent: Float,
        reachedEnd: Boolean,
        title: String,
        author: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadBook(readBook: ReadBookEntity)

    /** The reader's own statement, so this one is set rather than accumulated. */
    @Query("UPDATE ReadBookEntity SET finished = :finished WHERE bookId = :bookId")
    suspend fun setFinished(bookId: Int, finished: Boolean): Int

    /**
     * Keeps the record of a deleted book, without the book. It stays on the
     * shelf, with its time and its dates; a re-import is a new book and starts
     * a record of its own.
     */
    @Query("UPDATE ReadBookEntity SET bookId = NULL WHERE bookId = :bookId")
    suspend fun unlinkReadBook(bookId: Int)
}
