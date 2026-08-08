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
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity
import ua.acclorite.book_story.data.local.room.BookDatabase
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
}
