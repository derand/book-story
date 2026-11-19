/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.history.HistoryEvent
import ua.acclorite.book_story.presentation.history.HistoryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryState,
    refreshState: PullToRefreshState,
    listState: LazyListState,
    canScrollBackward: Boolean,
    focusRequester: FocusRequester,
    onEvent: (HistoryEvent) -> Unit
) {
    HistoryScaffold(
        refreshState = refreshState,
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