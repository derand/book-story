/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.placeholder

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/**
 * Empty placeholder. It usually is placed when the list is empty.
 *
 * @param modifier Modifier to be applied.
 * @param message The message.
 * @param icon Icon to be displayed.
 * @param actionTitle Optional action.
 * @param action On click action.
 */
@Composable
fun EmptyPlaceholder(
    modifier: Modifier = Modifier,
    message: String,
    icon: Painter,
    actionTitle: String? = null,
    action: () -> Unit = {}
) {
    Placeholder(
        modifier = modifier,
        message = message,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.secondary,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        actionTitle = actionTitle,
        action = action
    )
}