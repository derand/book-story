/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.navigation_bar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.presentation.navigator.NavigatorItem

@Composable
fun RowScope.NavigationBarItem(
    modifier: Modifier = Modifier,
    item: NavigatorItem,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        label = if (showLabel) {
            { NavigationTabLabel(item = item, tooltipPadding = 64.dp) }
        } else null,
        selected = isSelected,
        onClick = onClick,
        icon = {
            NavigationTabIcon(
                item = item,
                isSelected = isSelected,
                showLabel = showLabel,
                tooltipPadding = 32.dp
            )
        },
        modifier = modifier
    )
}