/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse.model

import androidx.compose.runtime.Immutable

/**
 * Adding books, in progress.
 *
 * [done] counts the books that are finished, so it starts at 0 and the book
 * named by [title] is the one being worked on — the count and the name always
 * describe the same moment.
 */
@Immutable
data class AddingBooks(
    val done: Int,
    val total: Int,
    val title: String
)
