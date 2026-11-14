/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.history.HistoryEvent
import ua.acclorite.book_story.presentation.history.HistoryState
import ua.acclorite.book_story.ui.common.components.common.Snackbar

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HistoryScaffold(
    state: HistoryState,
    refreshState: PullRefreshState,
    snackbarState: SnackbarHostState,
    listState: LazyListState,
    canScrollBackward: Boolean,
    focusRequester: FocusRequester,
    onEvent: (HistoryEvent) -> Unit
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .pullRefresh(refreshState),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            HistoryTopBar(
                canScrollBackward = canScrollBackward,
                state = state,
                focusRequester = focusRequester,
                onEvent = onEvent
            )
        },
        bottomBar = {
            Snackbar(snackbarState = snackbarState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            HistoryLayout(
                statistics = state.statistics,
                onEvent = onEvent,
                listState = listState,
                history = state.history,
                isLoading = state.isLoading,
                isRefreshing = state.isRefreshing,
            )

            HistoryEmptyPlaceholder(
                isLoading = state.isLoading,
                isRefreshing = state.isRefreshing,
                isHistoryEmpty = state.history.isEmpty(),
                loadFailed = state.loadFailed,
                onEvent = onEvent
            )

            HistoryRefreshIndicator(
                isRefreshing = state.isRefreshing,
                refreshState = refreshState
            )
        }
    }
}