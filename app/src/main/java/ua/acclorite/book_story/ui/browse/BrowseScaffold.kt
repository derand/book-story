/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.presentation.browse.BrowseState
import ua.acclorite.book_story.presentation.browse.model.BrowseLayout
import ua.acclorite.book_story.ui.theme.DefaultTransition

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BrowseScaffold(
    state: BrowseState,
    refreshState: PullRefreshState,
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
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BrowseTopBar(
                state = state,
                layout = layout,
                includedFilterItems = includedFilterItems,
                canScrollBackList = canScrollBackList,
                canScrollBackGrid = canScrollBackGrid,
                focusRequester = focusRequester,
                onEvent = onEvent
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            DefaultTransition(visible = !state.isLoading) {
                BrowseLayout(
                    files = state.files,
                    pinnedPaths = pinnedPaths,
                    layout = layout,
                    gridSize = gridSize,
                    autoGridSize = autoGridSize,
                    listState = listState,
                    gridState = gridState,
                    headerContent = { header, pinned ->
                        BrowseLayoutHeader(
                            header = header,
                            pinned = pinned,
                            pin = {
                                onEvent(
                                    BrowseEvent.OnUpdatePinnedPaths(
                                        path = header
                                    )
                                )
                            }
                        )
                    },
                    itemContent = { file, groupFiles ->
                        BrowseItem(
                            file = file,
                            layout = layout,
                            hasSelectedItems = state.hasSelectedItems,
                            onLongClick = {
                                onEvent(
                                    BrowseEvent.OnSelectFiles(
                                        files = groupFiles
                                    )
                                )
                            },
                            onClick = {
                                onEvent(
                                    BrowseEvent.OnSelectFiles(
                                        files = listOf(file)
                                    )
                                )
                            }
                        )
                    }
                )
            }

            BrowseEmptyPlaceholder(
                filesEmpty = state.files.isEmpty(),
                sourcesGranted = state.sourcesGranted,
                sourcesAvailable = state.sourcesAvailable,
                dialogHidden = state.dialog == null,
                isLoading = state.isLoading,
                isRefreshing = state.isRefreshing,
                loadFailed = state.loadFailed,
                onEvent = onEvent
            )

            BrowseRefreshIndicator(
                isRefreshing = state.isRefreshing,
                refreshState = refreshState
            )
        }
    }
}