/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.browse.sort.components

import androidx.compose.foundation.lazy.LazyListScope
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.browse.model.BrowseSortOrder
import ua.acclorite.book_story.ui.common.components.common.SortOrderList

fun LazyListScope.BrowseSortOption(
    sortOrder: BrowseSortOrder,
    sortOrderDescending: Boolean,
    onChange: (BrowseSortOrder, Boolean) -> Unit
) {
    SortOrderList(
        entries = BrowseSortOrder.entries,
        label = {
            when (it) {
                BrowseSortOrder.NAME -> R.string.sort_order_name
                BrowseSortOrder.FILE_FORMAT -> R.string.sort_order_file_format
                BrowseSortOrder.LAST_MODIFIED -> R.string.sort_order_last_modified
                BrowseSortOrder.FILE_SIZE -> R.string.sort_order_file_size
            }
        },
        selected = sortOrder,
        descending = sortOrderDescending,
        onChange = onChange
    )
}
