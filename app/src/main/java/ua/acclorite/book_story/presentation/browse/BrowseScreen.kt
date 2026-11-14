/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse

import android.os.Parcelable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.browse.BrowseContent
import ua.acclorite.book_story.ui.browse.BrowseEffects
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

@Parcelize
object BrowseScreen : Screen, Parcelable {

    @IgnoredOnParcel
    const val ADD_DIALOG = "add_dialog"

    @IgnoredOnParcel
    const val FILTER_BOTTOM_SHEET = "filter_bottom_sheet"

    @IgnoredOnParcel
    val refreshListChannel: Channel<Unit> = Channel(Channel.CONFLATED)

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<BrowseModel>()
        val settings = LocalSettings.current

        val state = screenModel.state.collectAsStateWithLifecycle()

        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()

        val focusRequester = remember { FocusRequester() }
        val refreshState = rememberPullRefreshState(
            refreshing = state.value.isRefreshing,
            onRefresh = {
                screenModel.onEvent(
                    BrowseEvent.OnRefreshList(
                        loading = false,
                        hideSearch = true
                    )
                )
            }
        )

        LaunchedEffect(
            settings.browseIncludedFilterItems.value,
            settings.browseSortOrderDescending.value,
            settings.browseSortOrder.value
        ) {
            screenModel.onEvent(
                BrowseEvent.OnRefreshList(
                    loading = false,
                    hideSearch = false
                )
            )
        }

        BrowseEffects(
            effects = screenModel.effects,
            focusRequester = focusRequester
        )

        BrowseContent(
            state = state.value,
            refreshState = refreshState,
            listState = listState,
            gridState = gridState,
            layout = settings.browseLayout.value,
            gridSize = settings.browseGridSize.value,
            autoGridSize = settings.browseAutoGridSize.value,
            includedFilterItems = settings.browseIncludedFilterItems.value,
            pinnedPaths = settings.browsePinnedPaths.value,
            canScrollBackList = listState.canScrollBackward,
            canScrollBackGrid = gridState.canScrollBackward,
            onEvent = screenModel::onEvent,
            focusRequester = focusRequester,
        )
    }
}