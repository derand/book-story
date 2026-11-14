/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.presentation.library.LibraryState
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryPage
import ua.acclorite.book_story.presentation.library.model.LibrarySortOrder
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.presentation.settings.SettingsEvent

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LibraryContent(
    state: LibraryState,
    titlePosition: LibraryTitlePosition,
    readButton: Boolean,
    showProgress: Boolean,
    showCategoryTabs: Boolean,
    showBookCount: Boolean,
    focusRequester: FocusRequester,
    pagerState: PagerState,
    doublePressExit: Boolean,
    categories: List<Category>,
    pages: List<LibraryPage>,
    perCategorySort: Boolean,
    sortOrder: LibrarySortOrder,
    sortOrderDescending: Boolean,
    layout: LibraryLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    refreshState: PullRefreshState,
    updateCategory: (SettingsEvent.OnUpdateCategory) -> Unit,
    changeSortOrder: (LibrarySortOrder) -> Unit,
    changeSortOrderDescending: (Boolean) -> Unit,
    onEvent: (LibraryEvent) -> Unit
) {
    LibraryDialog(
        dialog = state.dialog,
        books = state.books,
        categories = categories,
        selectedItemsCount = state.selectedItemsCount,
        onEvent = onEvent
    )

    LibraryBottomSheet(
        bottomSheet = state.bottomSheet,
        pages = pages,
        pagerState = pagerState,
        sortOrder = sortOrder,
        sortOrderDescending = sortOrderDescending,
        perCategorySort = perCategorySort,
        changeSortOrder = changeSortOrder,
        changeSortOrderDescending = changeSortOrderDescending,
        updateCategory = updateCategory,
        onEvent = onEvent
    )

    LibraryScaffold(
        state = state,
        titlePosition = titlePosition,
        readButton = readButton,
        showProgress = showProgress,
        showBookCount = showBookCount,
        showCategoryTabs = showCategoryTabs,
        focusRequester = focusRequester,
        pagerState = pagerState,
        layout = layout,
        gridSize = gridSize,
        autoGridSize = autoGridSize,
        pages = pages,
        onEvent = onEvent,
        refreshState = refreshState,
    )

    LibraryBackHandler(
        hasSelectedItems = state.hasSelectedItems,
        showSearch = state.showSearch,
        pagerState = pagerState,
        doublePressExit = doublePressExit,
        onEvent = onEvent
    )
}