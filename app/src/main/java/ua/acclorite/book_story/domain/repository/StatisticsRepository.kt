/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import ua.acclorite.book_story.domain.model.statistics.ReadingSession

interface StatisticsRepository {

    suspend fun addSession(session: ReadingSession): Result<Unit>

    /** See [ua.acclorite.book_story.data.local.room.StatisticsDao.anonymiseBookSessions]. */
    suspend fun anonymiseBookSessions(bookId: Int): Result<Unit>
}
