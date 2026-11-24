/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.dialog.DialogWithTextField

@Composable
fun BookInfoPathDialog(
    book: Book,
    onEvent: (BookInfoEvent) -> Unit
) {
    DialogWithTextField(
        initialValue = book.filePath.trim(),
        lengthLimit = 10000,
        allowBlank = false,
        onDismiss = {
            onEvent(BookInfoEvent.OnDismissDialog)
        },
        onAction = {
            onEvent(
                BookInfoEvent.OnActionPathDialog(
                    path = it.trim().replace("\n", "")
                )
            )
        }
    )
}