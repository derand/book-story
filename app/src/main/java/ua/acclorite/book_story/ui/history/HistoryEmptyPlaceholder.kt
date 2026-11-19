/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.common.AnimatedVisibility
import ua.acclorite.book_story.presentation.history.HistoryEvent
import ua.acclorite.book_story.ui.common.components.placeholder.EmptyPlaceholder
import ua.acclorite.book_story.ui.common.components.placeholder.ErrorPlaceholder
import ua.acclorite.book_story.ui.theme.Transitions

@Composable
fun BoxScope.HistoryEmptyPlaceholder(
    isLoading: Boolean,
    isRefreshing: Boolean,
    isHistoryEmpty: Boolean,
    loadFailed: Boolean,
    onEvent: (HistoryEvent) -> Unit
) {
    AnimatedVisibility(
        visible = !isLoading
                && isHistoryEmpty
                && !isRefreshing,
        modifier = Modifier.align(Alignment.Center),
        enter = Transitions.DefaultTransitionIn,
        exit = fadeOut(tween(0))
    ) {
        if (loadFailed) {
            ErrorPlaceholder(
                modifier = Modifier.align(Alignment.Center),
                errorMessage = stringResource(id = R.string.error_history_unreadable),
                icon = painterResource(id = R.drawable.error),
                actionTitle = stringResource(id = R.string.try_again)
            ) {
                onEvent(HistoryEvent.OnRefreshList(loading = true, hideSearch = false))
            }
            return@AnimatedVisibility
        }

        EmptyPlaceholder(
            modifier = Modifier.align(Alignment.Center),
            message = stringResource(id = R.string.history_empty),
            icon = painterResource(id = R.drawable.empty_history)
        )
    }
}
/** In place of the list while statistics are not collected: History is built from them. */
@Composable
fun BoxScope.HistoryStatisticsOffPlaceholder(
    onEvent: (HistoryEvent) -> Unit
) {
    EmptyPlaceholder(
        modifier = Modifier.align(Alignment.Center),
        message = stringResource(id = R.string.history_statistics_off),
        icon = painterResource(id = R.drawable.empty_history),
        actionTitle = stringResource(id = R.string.history_open_statistics_settings)
    ) {
        onEvent(HistoryEvent.OnNavigateToStatisticsSettings)
    }
}
