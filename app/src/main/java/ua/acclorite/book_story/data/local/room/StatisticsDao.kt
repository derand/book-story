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
import androidx.room.Transaction
import ua.acclorite.book_story.data.local.dto.ReadBookEntity
import ua.acclorite.book_story.data.local.dto.ReadingCoverageEntity
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity
import ua.acclorite.book_story.data.local.dto.SessionPace
import ua.acclorite.book_story.data.local.dto.SessionSpan

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

    /**
     * Pace of every session that has words to divide, for a typical figure.
     *
     * The duration is the *reading* time: overlay time earns no words, so
     * leaving it in the denominator would let a few minutes in the image viewer
     * halve a session's words per minute. Time in the book, days and streaks
     * still read the interval whole.
     */
    @Query(
        """
        SELECT (endTime - startTime - overlayMs) AS durationMs, wordsRead AS wordsRead
        FROM ReadingSessionEntity
        WHERE wordsRead > 0
        """
    )
    suspend fun getSessionPaces(): List<SessionPace>

    /** The same, for one book. */
    @Query(
        """
        SELECT (endTime - startTime - overlayMs) AS durationMs, wordsRead AS wordsRead
        FROM ReadingSessionEntity
        WHERE bookId = :bookId AND wordsRead > 0
        """
    )
    suspend fun getSessionPaces(bookId: Int): List<SessionPace>

    /** Every session's span and words, for figures that need the days too. */
    @Query("SELECT startTime, endTime, wordsRead FROM ReadingSessionEntity")
    suspend fun getSessionSpans(): List<SessionSpan>

    @Query("SELECT COUNT(*) FROM ReadBookEntity WHERE finished = 1")
    suspend fun countBooksRead(): Int

    /** Days on which this book was read at all, in the reader's own time zone. */
    @Query(
        """
        SELECT COUNT(DISTINCT date(startTime / 1000, 'unixepoch', 'localtime'))
        FROM ReadingSessionEntity
        WHERE bookId = :bookId
        """
    )
    suspend fun countActiveDays(bookId: Int): Int

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
     * Folds one finished session into the book's record, starting that record
     * on the first session.
     *
     * Both halves in one transaction, because the pair is a read-then-write:
     * the update reports whether a record existed, and between two separate
     * writes another writer could insert a second record for the same book. The
     * unique index on `bookId` would reject that insert, so without the
     * transaction the invariant would be enforced by an exception rather than
     * held by construction.
     */
    @Transaction
    suspend fun addSessionToBookRecord(
        bookId: Int,
        timeMs: Long,
        words: Int,
        endedAt: Long,
        coveragePercent: Float,
        reachedEnd: Boolean,
        title: String,
        author: String
    ) {
        val updated = accumulateSessionInReadBook(
            bookId = bookId,
            timeMs = timeMs,
            words = words,
            lastReadAt = endedAt,
            coveragePercent = coveragePercent,
            reachedEnd = reachedEnd,
            title = title,
            author = author
        )
        if (updated > 0) return

        // First session with this book: the record starts here, which is also
        // the only moment its firstReadAt is knowable.
        insertReadBook(
            ReadBookEntity(
                bookId = bookId,
                title = title,
                author = author,
                totalTimeMs = timeMs,
                totalWords = words,
                sessions = 1,
                firstReadAt = endedAt - timeMs,
                lastReadAt = endedAt,
                finished = reachedEnd,
                coveragePercent = coveragePercent
            )
        )
    }

    /**
     * The reader's own statement, set rather than accumulated — and recorded
     * even for a book this build has never measured, which is every book
     * already in the library: there is no backfill, so an empty record is the
     * truthful one. Transactional for the same reason as
     * [addSessionToBookRecord].
     */
    @Transaction
    suspend fun setBookFinished(
        bookId: Int,
        title: String,
        author: String,
        finished: Boolean,
        now: Long
    ) {
        val updated = updateFinished(bookId = bookId, finished = finished)
        if (updated > 0) return

        insertReadBook(
            ReadBookEntity(
                bookId = bookId,
                title = title,
                author = author,
                totalTimeMs = 0,
                totalWords = 0,
                sessions = 0,
                firstReadAt = now,
                lastReadAt = now,
                finished = finished,
                coveragePercent = 0f
            )
        )
    }

    /**
     * Accumulates in SQL rather than by reading the row and writing it back —
     * the totals are added where they live.
     *
     * `finished` only ever goes up: [reachedEnd] says this session got to the
     * end of the book, and a later session that stops earlier must not take
     * that back. Returns 0 when the book has no record yet.
     *
     * Called only from [addSessionToBookRecord], which supplies the transaction.
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
    suspend fun accumulateSessionInReadBook(
        bookId: Int,
        timeMs: Long,
        words: Int,
        lastReadAt: Long,
        coveragePercent: Float,
        reachedEnd: Boolean,
        title: String,
        author: String
    ): Int

    /**
     * Aborts on a duplicate rather than replacing it. `REPLACE` would delete the
     * existing record and insert this one, silently discarding the time, words
     * and sessions already accumulated against that book; aborting costs one
     * unrecorded update and says so in the log. Both callers hold a transaction
     * that should make the conflict unreachable — this is what happens if it
     * ever is not.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReadBook(readBook: ReadBookEntity)

    /** Called only from [setBookFinished], which supplies the transaction. */
    @Query("UPDATE ReadBookEntity SET finished = :finished WHERE bookId = :bookId")
    suspend fun updateFinished(bookId: Int, finished: Boolean): Int

    /**
     * Keeps the record of a deleted book, without the book: its time, dates and
     * counts stay in the lifetime figures, but it stops being named.
     *
     * Deleting a book in this app already means "gone" — the reader's history
     * for it is deleted too — so a record that went on naming it would quietly
     * break that promise. What is left is an unnamed row. A re-import is a new
     * book and starts a record of its own.
     */
    @Query("UPDATE ReadBookEntity SET bookId = NULL, title = '', author = '' WHERE bookId = :bookId")
    suspend fun unlinkReadBook(bookId: Int)

    /**
     * Erases every measurement. Unlike deleting a book — which anonymises its
     * sessions so the reading still counts towards the lifetime totals — this
     * leaves nothing behind anywhere, because that is the only thing it could
     * honestly mean.
     */
    @Transaction
    suspend fun deleteAllStatistics() {
        deleteAllSessions()
        deleteAllReadBooks()
        deleteAllCoverage()
    }

    @Query("DELETE FROM ReadingSessionEntity")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM ReadBookEntity")
    suspend fun deleteAllReadBooks()

    @Query("DELETE FROM ReadingCoverageEntity")
    suspend fun deleteAllCoverage()
}
