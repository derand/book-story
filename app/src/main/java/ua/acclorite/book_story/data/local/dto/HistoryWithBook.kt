/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Embedded
import androidx.room.Relation

data class HistoryWithBook(
    @Embedded val history: HistoryEntity,
    /**
     * Null for an orphan: nothing ties `bookId` to a row, so a history entry can
     * outlive its book (a database from an older build, a delete that stopped
     * halfway before deletes were one transaction). Non-null, Room would throw
     * for the whole list over one such row.
     */
    @Relation(
        parentColumn = "bookId",
        entityColumn = "id"
    )
    val book: BookEntity?
)