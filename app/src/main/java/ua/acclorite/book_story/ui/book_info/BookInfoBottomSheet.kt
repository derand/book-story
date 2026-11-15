/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.presentation.book_info.BookInfoState

@Composable
fun BookInfoBottomSheet(
    bottomSheet: BookInfoState.Sheet?,
    book: Book,
    file: File?,
    loadingFile: Boolean,
    canResetCover: Boolean,
    onEvent: (BookInfoEvent) -> Unit
) {
    when (bottomSheet) {
        null -> Unit

        BookInfoState.Sheet.ChangeCover -> {
            BookInfoChangeCoverBottomSheet(
                book = book,
                canResetCover = canResetCover,
                onEvent = onEvent
            )
        }

        BookInfoState.Sheet.Details -> {
            BookInfoDetailsBottomSheet(
                book = book,
                file = file,
                loadingFile = loadingFile,
                onEvent = onEvent
            )
        }
    }
}