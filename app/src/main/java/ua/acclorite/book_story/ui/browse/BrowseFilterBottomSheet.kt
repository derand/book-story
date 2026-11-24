/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.ui.browse.components.BrowseFilterSubcategory
import ua.acclorite.book_story.ui.browse.components.BrowseSortSubcategory
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.modal_bottom_sheet.ModalBottomSheet
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.settings.browse.display.BrowseDisplaySubcategory

private var initialPage = 0

@Composable
fun BrowseFilterBottomSheet(
    onEvent: (BrowseEvent) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage) { 3 }
    DisposableEffect(Unit) { onDispose { initialPage = pagerState.currentPage } }

    ModalBottomSheet(
        hasFixedHeight = true,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        dragHandle = {},
        onDismissRequest = {
            onEvent(BrowseEvent.OnDismissBottomSheet)
        },
        sheetGesturesEnabled = false
    ) {
        BrowseFilterBottomSheetTabRow(
            currentPage = pagerState.currentPage,
            scrollToPage = {
                scope.launch {
                    pagerState.animateScrollToPage(it)
                }
            }
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> {
                    val settings = LocalSettings.current
                    val sortOrder = settings.browseSortOrder.value
                    val sortOrderDescending = settings.browseSortOrderDescending.value

                    LazyColumnWithScrollbar(modifier = Modifier.fillMaxSize()) {
                        BrowseSortSubcategory(
                            sortOrder = sortOrder,
                            sortOrderDescending = sortOrderDescending,
                            onChange = { order, descending ->
                                settings.browseSortOrderDescending.update(descending)
                                settings.browseSortOrder.update(order)
                            },
                            showTitle = false,
                            showDivider = false
                        )
                    }
                }

                1 -> {
                    LazyColumnWithScrollbar(modifier = Modifier.fillMaxSize()) {
                        BrowseFilterSubcategory(
                            showTitle = false,
                            showDivider = false
                        )
                    }
                }

                2 -> {
                    LazyColumnWithScrollbar(modifier = Modifier.fillMaxSize()) {
                        BrowseDisplaySubcategory(
                            showTitle = false,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}