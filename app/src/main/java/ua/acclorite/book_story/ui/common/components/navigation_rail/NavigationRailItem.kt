/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.navigation_rail

import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.presentation.navigator.NavigatorItem
import ua.acclorite.book_story.ui.common.components.navigation_bar.NavigationTabIcon
import ua.acclorite.book_story.ui.common.components.navigation_bar.NavigationTabLabel

@Composable
fun NavigationRailItem(
    modifier: Modifier = Modifier,
    item: NavigatorItem,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    NavigationRailItem(
        label = if (showLabel) {
            { NavigationTabLabel(item = item, tooltipPadding = 48.dp) }
        } else null,
        selected = isSelected,
        onClick = onClick,
        icon = {
            NavigationTabIcon(
                item = item,
                isSelected = isSelected,
                showLabel = showLabel,
                tooltipPadding = 16.dp
            )
        },
        modifier = modifier
    )
}