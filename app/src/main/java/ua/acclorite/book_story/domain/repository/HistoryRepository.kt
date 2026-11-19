/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.acclorite.book_story.domain.model.history.HistorySession

/** History is read from the reading sessions; it has no table of its own. */
interface HistoryRepository {
    /**
     * Every session of a book still in the database, again after each change
     * to the sessions or the books. A failed read ends the flow with the error.
     */
    fun observeHistory(): Flow<List<HistorySession>>
}
