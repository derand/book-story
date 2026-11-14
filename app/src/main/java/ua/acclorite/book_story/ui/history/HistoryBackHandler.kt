/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import ua.acclorite.book_story.presentation.history.HistoryEvent

@Composable
fun HistoryBackHandler(
    showSearch: Boolean,
    onEvent: (HistoryEvent) -> Unit
) {
    BackHandler {
        if (showSearch) {
            onEvent(HistoryEvent.OnSearchVisibility(false))
            return@BackHandler
        }

        onEvent(HistoryEvent.OnNavigateToLibrary)
    }
}