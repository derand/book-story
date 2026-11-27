/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.navigation_bar

import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import ua.acclorite.book_story.presentation.navigator.NavigatorItem
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Composable
fun NavigationBar(tabs: List<NavigatorItem>, showLabels: Boolean) {
    val navigator = LocalNavigator.current
    val currentTab = rememberCurrentTab(tabs)

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                item = tab,
                isSelected = currentTab.value::class == tab.screen::class,
                showLabel = showLabels
            ) {
                navigator.push(tab.screen)
            }
        }
    }
}