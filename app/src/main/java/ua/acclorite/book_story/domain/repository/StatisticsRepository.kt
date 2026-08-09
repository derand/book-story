/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.domain.model.statistics.ReadBook
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.ReadingSession

interface StatisticsRepository {

    suspend fun addSession(session: ReadingSession): Result<Unit>

    /** See [ua.acclorite.book_story.data.local.room.StatisticsDao.anonymiseBookSessions]. */
    suspend fun anonymiseBookSessions(bookId: Int): Result<Unit>

    suspend fun getCoverage(bookId: Int): Result<ReadingCoverage?>

    suspend fun saveCoverage(coverage: ReadingCoverage): Result<Unit>

    suspend fun deleteCoverage(bookId: Int): Result<Unit>

    suspend fun getReadBook(bookId: Int): Result<ReadBook?>

    /** Folds a finished session into the book's record, creating it if needed. */
    suspend fun addSessionToReadBook(
        bookId: Int,
        title: String,
        author: String,
        timeMs: Long,
        words: Int,
        endedAt: Long,
        coveragePercent: Float,
        reachedEnd: Boolean
    ): Result<Unit>

    suspend fun setFinished(
        bookId: Int,
        title: String,
        author: String,
        finished: Boolean
    ): Result<Unit>

    /** See [ua.acclorite.book_story.data.local.room.StatisticsDao.unlinkReadBook]. */
    suspend fun unlinkReadBook(bookId: Int): Result<Unit>

    /**
     * Median words per minute across sessions — of one book when [bookId] is
     * given, of all reading otherwise. A median because one session spent
     * flicking through pages is enough to wreck a mean.
     */
    suspend fun getTypicalWordsPerMinute(bookId: Int? = null): Result<Int?>

    suspend fun countActiveDays(bookId: Int): Result<Int>

    /** Reading across the whole library, deleted books included. */
    suspend fun getLibraryStatistics(): Result<LibraryStatistics>

    /** See [ua.acclorite.book_story.data.local.room.StatisticsDao.deleteAllStatistics]. */
    suspend fun deleteAllStatistics(): Result<Unit>
}
