/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.navigation_bar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.presentation.navigator.NavigatorItem
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.common.Tooltip
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * The tab to show as selected: the navigator's top screen while it is one of
 * [tabs], else the last one that was (a pushed screen such as the reader keeps
 * the tab it came from). Shared by the bar and the rail.
 */
@Composable
fun rememberCurrentTab(tabs: List<NavigatorItem>): State<Screen> {
    val lastItem = LocalNavigator.current.lastItem.collectAsStateWithLifecycle()

    val currentTab = remember { mutableStateOf(lastItem.value) }
    LaunchedEffect(lastItem.value) {
        if (tabs.any { it.screen::class == lastItem.value::class }) {
            currentTab.value = lastItem.value
        }
    }
    return currentTab
}

/** The tab's label under its tooltip. */
@Composable
fun NavigationTabLabel(item: NavigatorItem, tooltipPadding: Dp) {
    Tooltip(
        text = stringResource(id = item.tooltip),
        padding = tooltipPadding
    ) {
        StyledText(
            text = stringResource(id = item.title),
            maxLines = 1
        )
    }
}

/**
 * The tab's icon under its tooltip. Without a label the icon carries the tab's
 * title, else TalkBack reads nothing for the tab (#167); with a label it stays
 * silent so the title is not read twice.
 */
@Composable
fun NavigationTabIcon(
    item: NavigatorItem,
    isSelected: Boolean,
    showLabel: Boolean,
    tooltipPadding: Dp
) {
    val description = if (showLabel) null else stringResource(id = item.title)

    Tooltip(
        text = stringResource(id = item.tooltip),
        padding = tooltipPadding
    ) {
        Crossfade(
            targetState = isSelected,
            animationSpec = tween(150),
            label = ""
        ) { selected ->
            Icon(
                painter = painterResource(
                    id = if (selected) item.selectedIcon else item.unselectedIcon
                ),
                contentDescription = description,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
