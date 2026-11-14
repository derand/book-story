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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.presentation.library.LibraryState
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryPage
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition

@OptIn(ExperimentalMaterialApi::class)
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
    refreshState: PullRefreshState,
    layout: LibraryLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    pages: List<LibraryPage>,
    onEvent: (LibraryEvent) -> Unit
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .pullRefresh(refreshState),
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

            LibraryRefreshIndicator(
                isRefreshing = state.isRefreshing,
                refreshState = refreshState
            )
        }
    }
}