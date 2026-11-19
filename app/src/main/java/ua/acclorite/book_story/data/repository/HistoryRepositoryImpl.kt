/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.mapper.book.BookMapper
import ua.acclorite.book_story.domain.model.history.HistorySession
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import ua.acclorite.book_story.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val database: BookDatabase,
    private val bookMapper: BookMapper
) : HistoryRepository {

    override fun observeHistory(): Flow<List<HistorySession>> =
        database.statisticsDao.observeSessionsWithBook().map { rows ->
            rows.mapNotNull { row ->
                val book = row.book ?: return@mapNotNull null
                HistorySession(
                    book = bookMapper.toBook(book),
                    session = ReadingSession(
                        bookId = book.id,
                        startTime = row.session.startTime,
                        endTime = row.session.endTime,
                        wordsRead = row.session.wordsRead,
                        overlayMs = row.session.overlayMs
                    )
                )
            }
        }
}
