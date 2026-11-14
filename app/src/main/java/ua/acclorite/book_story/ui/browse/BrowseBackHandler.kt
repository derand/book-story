/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import ua.acclorite.book_story.presentation.browse.BrowseEvent

@Composable
fun BrowseBackHandler(
    hasSelectedItems: Boolean,
    showSearch: Boolean,
    onEvent: (BrowseEvent) -> Unit
) {
    BackHandler {
        if (hasSelectedItems) {
            onEvent(BrowseEvent.OnClearSelectedFiles)
            return@BackHandler
        }

        if (showSearch) {
            onEvent(BrowseEvent.OnSearchVisibility(false))
            return@BackHandler
        }

        onEvent(BrowseEvent.OnNavigateToLibrary)
    }
}