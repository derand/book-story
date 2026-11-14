/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The pull-to-refresh spinner of Library, History and Browse, at the top of
 * the list. The list's scaffold takes `Modifier.pullToRefresh` with the same
 * [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ListRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
}
