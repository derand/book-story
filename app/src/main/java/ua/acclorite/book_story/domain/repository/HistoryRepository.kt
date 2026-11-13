/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.acclorite.book_story.domain.model.history.History

interface HistoryRepository {
    suspend fun getHistoryForBook(
        bookId: Int
    ): Result<History>

    suspend fun addHistory(
        history: History
    ): Result<Unit>

    /** Every entry, again after each change. A failed read ends the flow with the error. */
    fun observeHistory(): Flow<List<History>>

    suspend fun deleteWholeHistory(): Result<Unit>

    suspend fun deleteHistory(
        history: History
    ): Result<Unit>
}