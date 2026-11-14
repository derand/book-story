/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.history.HistoryEvent
import ua.acclorite.book_story.presentation.history.HistoryState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HistoryContent(
    state: HistoryState,
    refreshState: PullRefreshState,
    snackbarState: SnackbarHostState,
    listState: LazyListState,
    canScrollBackward: Boolean,
    focusRequester: FocusRequester,
    onEvent: (HistoryEvent) -> Unit
) {
    HistoryDialog(
        dialog = state.dialog,
        onEvent = onEvent
    )

    HistoryScaffold(
        refreshState = refreshState,
        snackbarState = snackbarState,
        state = state,
        listState = listState,
        canScrollBackward = canScrollBackward,
        onEvent = onEvent,
        focusRequester = focusRequester,
    )

    HistoryBackHandler(
        showSearch = state.showSearch,
        onEvent = onEvent
    )
}