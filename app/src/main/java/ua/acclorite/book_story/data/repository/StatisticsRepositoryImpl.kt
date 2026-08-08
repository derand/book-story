/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.data.local.dto.ReadBookEntity
import ua.acclorite.book_story.data.local.dto.ReadingCoverageEntity
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.domain.model.statistics.CoverageCodec
import ua.acclorite.book_story.domain.model.statistics.ReadBook
import ua.acclorite.book_story.domain.model.statistics.ReadingPace
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    private val database: BookDatabase
) : StatisticsRepository {

    // Mapped here rather than through a Mapper class: the row is the model, with
    // nothing to join and no UI type to build.
    override suspend fun addSession(session: ReadingSession): Result<Unit> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.insertSession(
                    ReadingSessionEntity(
                        bookId = session.bookId,
                        startTime = session.startTime,
                        endTime = session.endTime,
                        wordsRead = session.wordsRead
                    )
                )
            }
        }

    override suspend fun anonymiseBookSessions(bookId: Int): Result<Unit> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.anonymiseBookSessions(bookId = bookId)
            }
        }

    override suspend fun getCoverage(bookId: Int): Result<ReadingCoverage?> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.getCoverage(bookId)?.let { entity ->
                    ReadingCoverage(
                        bookId = entity.bookId,
                        itemCount = entity.itemCount,
                        bookWords = entity.bookWords,
                        covered = CoverageCodec.decode(entity.intervals),
                        coveredWords = entity.coveredWords
                    )
                }
            }
        }

    override suspend fun saveCoverage(coverage: ReadingCoverage): Result<Unit> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.saveCoverage(
                    ReadingCoverageEntity(
                        bookId = coverage.bookId,
                        itemCount = coverage.itemCount,
                        bookWords = coverage.bookWords,
                        intervals = CoverageCodec.encode(coverage.covered),
                        coveredWords = coverage.coveredWords
                    )
                )
            }
        }

    override suspend fun deleteCoverage(bookId: Int): Result<Unit> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.deleteCoverage(bookId = bookId)
            }
        }

    override suspend fun getReadBook(bookId: Int): Result<ReadBook?> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.getReadBook(bookId)?.let { entity ->
                    ReadBook(
                        id = entity.id,
                        bookId = entity.bookId,
                        title = entity.title,
                        author = entity.author,
                        totalTimeMs = entity.totalTimeMs,
                        totalWords = entity.totalWords,
                        sessions = entity.sessions,
                        firstReadAt = entity.firstReadAt,
                        lastReadAt = entity.lastReadAt,
                        finished = entity.finished,
                        coveragePercent = entity.coveragePercent
                    )
                }
            }
        }

    override suspend fun addSessionToReadBook(
        bookId: Int,
        title: String,
        author: String,
        timeMs: Long,
        words: Int,
        endedAt: Long,
        coveragePercent: Float,
        reachedEnd: Boolean
    ): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            val updated = database.statisticsDao.addSessionToReadBook(
                bookId = bookId,
                timeMs = timeMs,
                words = words,
                lastReadAt = endedAt,
                coveragePercent = coveragePercent,
                reachedEnd = reachedEnd,
                title = title,
                author = author
            )
            if (updated > 0) return@withContext

            // First session with this book: the record starts here, which is
            // also the only moment its firstReadAt is knowable.
            database.statisticsDao.insertReadBook(
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
    }

    override suspend fun setFinished(
        bookId: Int,
        title: String,
        author: String,
        finished: Boolean
    ): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            val updated = database.statisticsDao.setFinished(bookId = bookId, finished = finished)
            if (updated > 0) return@withContext

            // Marked without ever having been read here — an empty record is
            // still the truthful one.
            val now = System.currentTimeMillis()
            database.statisticsDao.insertReadBook(
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
    }

    override suspend fun getTypicalWordsPerMinute(bookId: Int?): Result<Int?> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val paces =
                    if (bookId == null) database.statisticsDao.getSessionPaces()
                    else database.statisticsDao.getSessionPaces(bookId)

                ReadingPace.typical(
                    paces.mapNotNull { pace ->
                        ReadingPace.wordsPerMinute(pace.durationMs, pace.wordsRead)
                    }
                )
            }
        }

    override suspend fun countActiveDays(bookId: Int): Result<Int> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.countActiveDays(bookId)
            }
        }

    override suspend fun unlinkReadBook(bookId: Int): Result<Unit> =
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                database.statisticsDao.unlinkReadBook(bookId = bookId)
            }
        }
}
