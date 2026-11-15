/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.presentation.book_info.BookInfoState

@Composable
fun BookInfoDialog(
    dialog: BookInfoState.Dialog?,
    book: Book,
    categories: List<Category>,
    onEvent: (BookInfoEvent) -> Unit
) {
    when (dialog) {
        null -> Unit

        BookInfoState.Dialog.Delete -> {
            BookInfoDeleteDialog(
                onEvent = onEvent
            )
        }

        BookInfoState.Dialog.Move -> {
            BookInfoMoveDialog(
                book = book,
                categories = categories,
                onEvent = onEvent
            )
        }

        BookInfoState.Dialog.Title -> {
            BookInfoTitleDialog(
                book = book,
                onEvent = onEvent
            )
        }

        BookInfoState.Dialog.Author -> {
            BookInfoAuthorDialog(
                book = book,
                onEvent = onEvent
            )
        }

        BookInfoState.Dialog.Description -> {
            BookInfoDescriptionDialog(
                book = book,
                onEvent = onEvent
            )
        }

        BookInfoState.Dialog.Path -> {
            BookInfoPathDialog(
                book = book,
                onEvent = onEvent
            )
        }
    }
}