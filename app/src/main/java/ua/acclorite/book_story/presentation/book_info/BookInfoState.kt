/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.book_info

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.statistics.BookStatistics

@Immutable
data class BookInfoState(
    val book: Book = Book.default,

    val file: File? = null,
    val loadingFile: Boolean = true,

    val canResetCover: Boolean = false,

    /** True while the book's copy is being made or given up; both take real time. */
    val changingLocalCopy: Boolean = false,

    /** Null until read back; [BookStatistics.none] when nothing was ever read. */
    val statistics: BookStatistics? = null,

    val dialog: Dialog? = null,
    val bottomSheet: Sheet? = null
) {
    sealed interface Dialog {
        data object Title : Dialog
        data object Author : Dialog
        data object Description : Dialog
        data object Path : Dialog
        data object Delete : Dialog
        data object Move : Dialog
    }

    sealed interface Sheet {
        data object Details : Sheet
        data object ChangeCover : Sheet
    }
}