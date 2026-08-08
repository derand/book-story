/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One reading session: from the moment a book's text is shown to the moment the
 * reader is stopped. The detail layer of the statistics — per-book figures are
 * read from here.
 *
 * Deleting a book does **not** delete its sessions; it sets [bookId] to null.
 * The per-book detail then stops matching `WHERE bookId = :id` and disappears,
 * while the day, the duration and the words stay in the lifetime totals. A
 * deleted book therefore cannot retroactively shorten a streak or shrink the
 * total time, and "forget this book entirely" is still available later as a
 * plain delete.
 */
@Entity(indices = [Index("bookId"), Index("startTime")])
data class ReadingSessionEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    /** The book read, or null once that book has been deleted. */
    val bookId: Int?,
    val startTime: Long,
    /**
     * End of the session. Trailing idle is capped when the row is written, so
     * a device left open on a page cannot inflate it; idle *between* two
     * reading spells is kept in full, deliberately.
     */
    val endTime: Long,
    /** Words credited to this session — the volume read, repeats included. */
    val wordsRead: Int
)
