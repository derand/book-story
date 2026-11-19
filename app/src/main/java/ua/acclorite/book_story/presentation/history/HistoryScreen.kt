/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package ua.acclorite.book_story.presentation.history

import android.os.Parcelable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.history.HistoryContent
import ua.acclorite.book_story.ui.history.HistoryEffects

@Parcelize
object HistoryScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<HistoryModel>()

        val state = screenModel.state.collectAsStateWithLifecycle()

        val listState = rememberLazyListState()

        val focusRequester = remember { FocusRequester() }
        val refreshState = rememberPullToRefreshState()

        HistoryEffects(
            effects = screenModel.effects,
            focusRequester = focusRequester
        )

        HistoryContent(
            refreshState = refreshState,
            listState = listState,
            state = state.value,
            canScrollBackward = listState.canScrollBackward,
            onEvent = screenModel::onEvent,
            focusRequester = focusRequester,
        )
    }
}