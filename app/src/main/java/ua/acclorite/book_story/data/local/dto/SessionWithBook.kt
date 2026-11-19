/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Embedded
import androidx.room.Relation

/** A reading session with the book it was read in, for the History list. */
data class SessionWithBook(
    @Embedded val session: ReadingSessionEntity,
    /**
     * Null if no book has the session's `bookId`. A deleted book's sessions
     * are unlinked (`bookId = NULL`) in the same transaction as the delete,
     * so this should not happen; non-null, Room would throw for the whole
     * list over one such row.
     */
    @Relation(
        parentColumn = "bookId",
        entityColumn = "id"
    )
    val book: BookEntity?
)
