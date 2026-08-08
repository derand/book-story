/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ua.acclorite.book_story.data.local.dto.ReadingSessionEntity

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
}
