/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.ui.common.components.common.AnimatedVisibility
import ua.acclorite.book_story.ui.common.components.placeholder.EmptyPlaceholder
import ua.acclorite.book_story.ui.theme.Transitions

@Composable
fun BoxScope.BrowseEmptyPlaceholder(
    filesEmpty: Boolean,
    sourcesGranted: Int,
    sourcesAvailable: Int,
    dialogHidden: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    navigateToBrowseSettings: (BrowseEvent.OnNavigateToBrowseSettings) -> Unit
) {
    AnimatedVisibility(
        visible = !isLoading
                && dialogHidden
                && filesEmpty
                && !isRefreshing,
        modifier = Modifier.align(Alignment.Center),
        enter = Transitions.DefaultTransitionIn,
        exit = Transitions.NoExitAnimation
    ) {
        EmptyPlaceholder(
            message = stringResource(
                id = browseEmptyMessage(sourcesGranted, sourcesAvailable)
            ),
            icon = painterResource(id = R.drawable.empty_browse),
            actionTitle = stringResource(id = R.string.set_up_scanning),
            action = {
                navigateToBrowseSettings(BrowseEvent.OnNavigateToBrowseSettings)
            }
        )
    }
}

/**
 * What an empty Browse says.
 *
 * Nothing to show is two different situations, and only one of them is an
 * invitation. Folders granted and none of them answering is a fault, and
 * offering to "expand the library with your downloads" to someone whose three
 * folders have all gone quiet explains nothing. The way through stays the same
 * — the settings screen is where the state is now reported.
 */
@StringRes
internal fun browseEmptyMessage(sourcesGranted: Int, sourcesAvailable: Int): Int = when {
    sourcesGranted > 0 && sourcesAvailable == 0 -> R.string.browse_sources_unavailable
    else -> R.string.browse_empty
}
