/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.ui.common.components.common.ListRefreshIndicator
import ua.acclorite.book_story.presentation.library.LibraryState
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryPage
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScaffold(
    state: LibraryState,
    titlePosition: LibraryTitlePosition,
    readButton: Boolean,
    showProgress: Boolean,
    showCategoryTabs: Boolean,
    showBookCount: Boolean,
    focusRequester: FocusRequester,
    pagerState: PagerState,
    refreshState: PullToRefreshState,
    layout: LibraryLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    pages: List<LibraryPage>,
    onEvent: (LibraryEvent) -> Unit
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = state.isRefreshing,
                state = refreshState,
                onRefresh = {
                    onEvent(LibraryEvent.OnRefreshList(loading = false, hideSearch = true))
                }
            ),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LibraryTopBar(
                state = state,
                showBookCount = showBookCount,
                showCategoryTabs = showCategoryTabs,
                focusRequester = focusRequester,
                pagerState = pagerState,
                pages = pages,
                onEvent = onEvent
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LibraryPager(
                pages = pages,
                pagerState = pagerState,
                layout = layout,
                gridSize = gridSize,
                autoGridSize = autoGridSize,
                state = state,
                titlePosition = titlePosition,
                readButton = readButton,
                showProgress = showProgress,
                onEvent = onEvent
            )

            ListRefreshIndicator(state = refreshState, isRefreshing = state.isRefreshing)
        }
    }
}