/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.dialog.Dialog

@Composable
fun BookInfoDeleteDialog(
    onEvent: (BookInfoEvent) -> Unit
) {
    Dialog(
        title = stringResource(id = R.string.delete_book),
        icon = Icons.Outlined.DeleteOutline,
        description = stringResource(
            id = R.string.delete_book_description
        ),
        onDismiss = { onEvent(BookInfoEvent.OnDismissDialog) },
        withContent = false,
        actionEnabled = true,
        onAction = {
            onEvent(BookInfoEvent.OnActionDeleteDialog)
        }
    )
}