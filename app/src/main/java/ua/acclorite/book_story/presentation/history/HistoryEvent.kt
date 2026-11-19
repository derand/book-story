/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.history

import androidx.compose.runtime.Immutable

@Immutable
sealed class HistoryEvent {
    data class OnRefreshList(
        val loading: Boolean,
        val hideSearch: Boolean
    ) : HistoryEvent()

    data class OnSearchVisibility(
        val show: Boolean
    ) : HistoryEvent()

    data object OnRequestFocus : HistoryEvent()

    data class OnSearchQueryChange(
        val query: String
    ) : HistoryEvent()

    data object OnSearch : HistoryEvent()

    data object OnNavigateToLibrary : HistoryEvent()

    data object OnNavigateToStatistics : HistoryEvent()

    /** Where statistics are switched on and off, and deleted. */
    data object OnNavigateToStatisticsSettings : HistoryEvent()

    data class OnNavigateToBookInfo(
        val bookId: Int
    ) : HistoryEvent()

    data class OnNavigateToReader(
        val bookId: Int
    ) : HistoryEvent()
}