/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.domain.model.statistics.ReadBook
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.ReadingSession

interface StatisticsRepository {

    /**
     * Stores [session] and folds it into its book's record, creating the record
     * if needed — both or neither.
     */
    suspend fun recordSession(
        session: ReadingSession,
        title: String,
        author: String,
        coveragePercent: Float,
        reachedEnd: Boolean
    ): Result<Unit>

    suspend fun getCoverage(bookId: Int): Result<ReadingCoverage?>

    suspend fun saveCoverage(coverage: ReadingCoverage): Result<Unit>

    suspend fun getReadBook(bookId: Int): Result<ReadBook?>

    suspend fun setFinished(
        bookId: Int,
        title: String,
        author: String,
        finished: Boolean
    ): Result<Unit>

    /**
     * Median words per minute across sessions — of one book when [bookId] is
     * given, of all reading otherwise. A median because one session spent
     * flicking through pages is enough to wreck a mean.
     */
    suspend fun getTypicalWordsPerMinute(bookId: Int? = null): Result<Int?>

    suspend fun countActiveDays(bookId: Int): Result<Int>

    /** Reading across the whole library, deleted books included. */
    suspend fun getLibraryStatistics(): Result<LibraryStatistics>

    /** [getLibraryStatistics] now and again after each change to the reading records. */
    fun observeLibraryStatistics(): Flow<Result<LibraryStatistics>>

    /** See [ua.acclorite.book_story.data.local.room.StatisticsDao.deleteAllStatistics]. */
    suspend fun deleteAllStatistics(): Result<Unit>
}
