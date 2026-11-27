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
 * Empty placeholder.
 * It usually placed when something went wrong and you need to display large error message.
 *
 * @param modifier Modifier to be applied.
 * @param errorMessage The error message.
 * @param icon Icon to be displayed.
 * @param actionTitle Optional action.
 * @param action On click action.
 */
@Composable
fun ErrorPlaceholder(
    modifier: Modifier = Modifier,
    errorMessage: String,
    icon: Painter,
    actionTitle: String? = null,
    action: () -> Unit = {}
) {
    Placeholder(
        modifier = modifier,
        message = errorMessage,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.error,
        textColor = MaterialTheme.colorScheme.error,
        actionTitle = actionTitle,
        action = action
    )
}