/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalMaterialApi::class)

package ua.acclorite.book_story.presentation.history

import android.os.Parcelable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.history.HistoryContent
import ua.acclorite.book_story.ui.history.HistoryEffects

@Parcelize
object HistoryScreen : Screen, Parcelable {

    @IgnoredOnParcel
    const val DELETE_WHOLE_HISTORY_DIALOG = "delete_whole_history_dialog"

    @IgnoredOnParcel
    val insertHistoryChannel: Channel<Int> = Channel(Channel.CONFLATED)

    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<HistoryModel>()

        val state = screenModel.state.collectAsStateWithLifecycle()

        val listState = rememberLazyListState()

        val snackbarState = remember { SnackbarHostState() }
        val focusRequester = remember { FocusRequester() }
        val refreshState = rememberPullRefreshState(
            refreshing = state.value.isRefreshing,
            onRefresh = {
                screenModel.onEvent(
                    HistoryEvent.OnRefreshList(
                        loading = false,
                        hideSearch = true
                    )
                )
            }
        )

        HistoryEffects(
            effects = screenModel.effects,
            focusRequester = focusRequester,
            snackbarState = snackbarState,
            onEvent = screenModel::onEvent
        )

        HistoryContent(
            refreshState = refreshState,
            snackbarState = snackbarState,
            listState = listState,
            state = state.value,
            canScrollBackward = listState.canScrollBackward,
            onEvent = screenModel::onEvent,
            focusRequester = focusRequester,
        )
    }
}