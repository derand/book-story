/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.presentation.browse.BrowseState
import ua.acclorite.book_story.presentation.browse.model.BrowseLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseContent(
    state: BrowseState,
    refreshState: PullToRefreshState,
    listState: LazyListState,
    gridState: LazyGridState,
    layout: BrowseLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    includedFilterItems: List<String>,
    pinnedPaths: List<String>,
    canScrollBackList: Boolean,
    canScrollBackGrid: Boolean,
    focusRequester: FocusRequester,
    onEvent: (BrowseEvent) -> Unit
) {
    BrowseDialog(
        dialog = state.dialog,
        loadingAddDialog = state.loadingAddDialog,
        addingBooks = state.addingBooks,
        onEvent = onEvent,
        selectedBooksAddDialog = state.selectedBooksAddDialog,
    )

    BrowseBottomSheet(
        bottomSheet = state.bottomSheet,
        onEvent = onEvent
    )

    BrowseScaffold(
        state = state,
        refreshState = refreshState,
        listState = listState,
        gridState = gridState,
        layout = layout,
        gridSize = gridSize,
        autoGridSize = autoGridSize,
        includedFilterItems = includedFilterItems,
        pinnedPaths = pinnedPaths,
        canScrollBackList = canScrollBackList,
        canScrollBackGrid = canScrollBackGrid,
        focusRequester = focusRequester,
        onEvent = onEvent
    )

    BrowseBackHandler(
        hasSelectedItems = state.hasSelectedItems,
        showSearch = state.showSearch,
        onEvent = onEvent
    )
}