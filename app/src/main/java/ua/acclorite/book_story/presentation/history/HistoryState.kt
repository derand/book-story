/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.history

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.core.Dialog
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.presentation.history.model.GroupedHistory

@Immutable
data class HistoryState(
    val history: List<GroupedHistory> = emptyList(),

    /** Null until read back; the card stays out of the way until then. */
    val statistics: LibraryStatistics? = null,

    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,

    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val hasFocused: Boolean = false,

    val dialog: Dialog? = null
)