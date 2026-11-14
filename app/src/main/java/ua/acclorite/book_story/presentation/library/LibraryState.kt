/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.use_case.backup.RestoreNotice
import ua.acclorite.book_story.core.BottomSheet
import ua.acclorite.book_story.core.Dialog
import ua.acclorite.book_story.presentation.library.model.SelectableBook

@Immutable
data class LibraryState(
    /** The books shown: the whole library, or what the search matches. */
    val books: List<SelectableBook> = emptyList(),
    /** The whole library, whatever the search: it decides which pages exist. */
    val library: List<Book> = emptyList(),

    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** The last read failed: the empty list is not an empty one. */
    val loadFailed: Boolean = false,

    val selectedItemsCount: Int = 0,
    val hasSelectedItems: Boolean = false,

    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val hasFocused: Boolean = false,

    val dialog: Dialog? = null,
    val bottomSheet: BottomSheet? = null,

    /** Apart from [dialog]: it arrives on its own, whatever else is open. */
    val restoreNotice: RestoreNotice? = null
)