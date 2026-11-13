/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Embedded

/** A library book with its latest history entry's time, read in one query. */
data class LibraryBookRow(
    @Embedded val book: BookEntity,
    /** Null for a book never opened. */
    val lastOpened: Long?
)
