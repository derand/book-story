/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.mapper.history.HistoryMapper
import ua.acclorite.book_story.domain.model.history.History
import ua.acclorite.book_story.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val database: BookDatabase,
    private val historyMapper: HistoryMapper
) : HistoryRepository {

    override suspend fun getHistoryForBook(bookId: Int): Result<History> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.historyDao.getHistoryForBook(bookId)?.let { historyMapper.toHistory(it) }
                ?: throw NoSuchElementException("Could not get history from [$bookId].")
        }
    }

    override suspend fun addHistory(history: History): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.historyDao.insertHistory(historyMapper.toHistoryEntity(history))
        }
    }

    override suspend fun getHistory(): Result<List<History>> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.historyDao.getHistoryWithBook().mapNotNull { historyMapper.toHistory(it) }
        }
    }

    override suspend fun deleteWholeHistory(): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.historyDao.deleteWholeHistory().also {
                if (it == 0) throw Exception("Could not delete whole history in database.")
            }
        }
    }

    override suspend fun deleteHistory(history: History): Result<Unit> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            database.historyDao.deleteHistory(historyMapper.toHistoryEntity(history)).also {
                if (it == 0) throw Exception("Could not delete history in database.")
            }
        }
    }
}