/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Embedded

/** A library book with when it was last read, read in one query. */
data class LibraryBookRow(
    @Embedded val book: BookEntity,
    /** Null for a book never read (no reading session recorded). */
    val lastOpened: Long?
)
