/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.library.sort.components

import androidx.compose.foundation.lazy.LazyListScope
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.library.model.LibrarySortOrder
import ua.acclorite.book_story.ui.common.components.common.SortOrderList

fun LazyListScope.LibrarySortOption(
    sortOrder: LibrarySortOrder,
    sortOrderDescending: Boolean,
    onChange: (LibrarySortOrder, Boolean) -> Unit
) {
    SortOrderList(
        entries = LibrarySortOrder.entries,
        label = {
            when (it) {
                LibrarySortOrder.NAME -> R.string.sort_order_name
                LibrarySortOrder.LAST_READ -> R.string.sort_order_last_read
                LibrarySortOrder.PROGRESS -> R.string.sort_order_progress
                LibrarySortOrder.AUTHOR -> R.string.sort_order_author
            }
        },
        selected = sortOrder,
        descending = sortOrderDescending,
        onChange = onChange
    )
}
