/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.navigator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.helpers.LocalActivity

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun NavigatorTabs(
    currentTab: Screen,
    transitionSpec: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    navigationBar: @Composable () -> Unit,
    navigationRail: @Composable () -> Unit,
    content: @Composable (Screen) -> Unit
) {
    val windowClass = calculateWindowSizeClass(activity = LocalActivity.current)
    val tabletUI = remember(windowClass) {
        windowClass.widthSizeClass != WindowWidthSizeClass.Compact
    }
    val layoutDirection = LocalLayoutDirection.current
    // Each tab keeps its own saved state across a switch to another tab; all
    // three together are one screen to the navigator, which keeps them while
    // something is pushed over them.
    val tabState = rememberSaveableStateHolder()
    var showNavigatorBottomSheet by rememberSaveable { mutableStateOf(false) }

    // One instance: the local is static, so a new lambda would recompose
    // every tab under it.
    val openNavigatorSheet: () -> Unit = remember { { showNavigatorBottomSheet = true } }

    if (showNavigatorBottomSheet) {
        NavigatorBottomSheet(dismiss = { showNavigatorBottomSheet = false })
    }

    CompositionLocalProvider(LocalOpenNavigatorSheet provides openNavigatorSheet) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!tabletUI) navigationBar()
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (tabletUI) navigationRail()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = paddingValues.calculateBottomPadding(),
                            start = if (!tabletUI) paddingValues.calculateStartPadding(layoutDirection)
                            else 0.dp
                        )
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = transitionSpec,
                        content = { tab ->
                            tabState.SaveableStateProvider(tab) {
                                content(tab)
                            }
                        }
                    )
                }
            }
        }
    }
}
