/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library

import android.os.Parcelable
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.library.model.libraryPages
import ua.acclorite.book_story.presentation.library.model.pageAt
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.settings.CategoriesModel
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.library.LibraryContent
import ua.acclorite.book_story.ui.library.LibraryEffects
import ua.acclorite.book_story.ui.library.LibraryRestoreDialog

@Parcelize
object LibraryScreen : Screen, Parcelable {

    @IgnoredOnParcel
    const val MOVE_DIALOG = "move_dialog"

    @IgnoredOnParcel
    const val DELETE_DIALOG = "delete_dialog"

    @IgnoredOnParcel
    const val FILTER_BOTTOM_SHEET = "filter_bottom_sheet"

    @IgnoredOnParcel
    val scrollToPageCompositionChannel: Channel<Int> = Channel(Channel.CONFLATED)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<LibraryModel>()
        val categoriesModel = hiltViewModel<CategoriesModel>()
        val settings = LocalSettings.current

        val state = screenModel.state.collectAsStateWithLifecycle()
        val categoriesState = categoriesModel.categories.collectAsStateWithLifecycle()

        val categories = categoriesState.value
        val library = state.value.library
        val shown = state.value.books
        val alwaysShowDefault = settings.libraryShowDefaultTab.value
        val perCategorySort = settings.libraryPerCategorySort.value
        val sortOrder = settings.librarySortOrder.value
        val sortOrderDescending = settings.librarySortOrderDescending.value
        val defaultTitle = stringResource(id = R.string.default_tab)
        val pages = remember(
            categories, library, shown, alwaysShowDefault,
            perCategorySort, sortOrder, sortOrderDescending, defaultTitle
        ) {
            libraryPages(
                categories = categories,
                library = library,
                shown = shown,
                alwaysShowDefault = alwaysShowDefault,
                perCategorySort = perCategorySort,
                sortOrder = sortOrder,
                sortOrderDescending = sortOrderDescending,
                defaultTitle = defaultTitle
            )
        }

        val focusRequester = remember { FocusRequester() }
        val refreshState = rememberPullToRefreshState()

        val pagerState = rememberPagerState { pages.size }

        // The pager keeps an index, so a reorder or a deleted category would
        // leave it on another category. Keep the shown one by id instead, in
        // settings, so a launch opens it too.
        val shownCategory = settings.libraryShownCategory
        val currentPages = rememberUpdatedState(pages)
        // Until the books are in, the Default page may be missing and every
        // page after it one place to the left: neither follow nor record.
        val pagesReady = !state.value.isLoading && !state.value.loadFailed

        LaunchedEffect(Unit) {
            scrollToPageCompositionChannel.receiveAsFlow().collectLatest {
                shownCategory.update(currentPages.value.pageAt(it).category.id)
                pagerState.scrollToPage(it)
            }
        }

        LaunchedEffect(pages, pagesReady) {
            if (!pagesReady) return@LaunchedEffect
            val index = pages.indexOfFirst { it.category.id == shownCategory.lastValue }
            if (index >= 0 && index != pagerState.currentPage) {
                try {
                    pagerState.scrollToPage(index)
                } catch (e: CancellationException) {
                    // A newer scroll (the channel above) took over; keep tracking.
                    currentCoroutineContext().ensureActive()
                }
            }
            // Emits the current page first, so a deleted category is replaced
            // by the one now shown.
            snapshotFlow { pagerState.settledPage }.collect {
                shownCategory.update(pages.pageAt(it).category.id)
            }
        }

        LibraryEffects(
            effects = screenModel.effects,
            focusRequester = focusRequester
        )

        LibraryContent(
            state = state.value,
            titlePosition = settings.libraryTitlePosition.value,
            readButton = settings.libraryShowReadButton.value,
            showProgress = settings.libraryShowProgress.value,
            showCategoryTabs = settings.libraryShowCategoryTabs.value,
            showBookCount = settings.libraryShowBookCount.value,
            focusRequester = focusRequester,
            pagerState = pagerState,
            doublePressExit = settings.doublePressExit.value,
            layout = settings.libraryLayout.value,
            gridSize = settings.libraryGridSize.value,
            autoGridSize = settings.libraryAutoGridSize.value,
            categories = categoriesState.value,
            pages = pages,
            sortOrder = settings.librarySortOrder.value,
            sortOrderDescending = settings.librarySortOrderDescending.value,
            perCategorySort = settings.libraryPerCategorySort.value,
            refreshState = refreshState,
            updateCategory = categoriesModel::onEvent,
            changeSortOrder = { settings.librarySortOrder.update(it) },
            changeSortOrderDescending = { settings.librarySortOrderDescending.update(it) },
            onEvent = screenModel::onEvent
        )

        state.value.restoreNotice?.let { notice ->
            LibraryRestoreDialog(
                notice = notice,
                onEvent = screenModel::onEvent
            )
        }
    }
}