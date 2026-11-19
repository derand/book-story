/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.history

import androidx.compose.runtime.Immutable

@Immutable
sealed class HistoryEffect {
    data object OnRequestFocus : HistoryEffect()

    data object OnNavigateToLibrary : HistoryEffect()

    data object OnNavigateToStatistics : HistoryEffect()

    data object OnNavigateToStatisticsSettings : HistoryEffect()

    data class OnNavigateToBookInfo(
        val bookId: Int
    ) : HistoryEffect()

    data class OnNavigateToReader(
        val bookId: Int
    ) : HistoryEffect()
}