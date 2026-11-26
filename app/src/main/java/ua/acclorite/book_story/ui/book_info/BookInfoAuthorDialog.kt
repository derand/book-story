/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.dialog.DialogWithTextField

@Composable
fun BookInfoAuthorDialog(
    book: Book,
    onEvent: (BookInfoEvent) -> Unit
) {
    DialogWithTextField(
        initialValue = book.author.literalOrNull() ?: "",
        lengthLimit = 100,
        onDismiss = {
            onEvent(BookInfoEvent.OnDismissDialog)
        },
        onAction = {
            onEvent(
                BookInfoEvent.OnActionAuthorDialog(
                    author = if (it.isBlank()) UIText.StringResource(R.string.unknown_author)
                    else UIText.StringValue(it.trim().replace("\n", ""))
                )
            )
        }
    )
}