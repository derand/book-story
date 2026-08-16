/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.core.BottomSheet
import ua.acclorite.book_story.core.Drawer
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book
import androidx.compose.ui.text.AnnotatedString
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch

@Immutable
data class ReaderState(
    val book: Book = Book.default,
    val text: List<ReaderText> = emptyList(),
    val notes: Map<String, AnnotatedString> = emptyMap(),
    val currentNote: AnnotatedString? = null,
    /** The image shown in the full-screen viewer; null when it is closed. */
    val fullscreenImage: ReaderText.Image? = null,
    val listState: LazyListState = LazyListState(),

    val currentChapter: Chapter? = null,
    val currentChapterProgress: Float = 0f,

    val search: ReaderSearch = ReaderSearch(),

    val errorMessage: UIText? = null,
    val isLoading: Boolean = true,

    val showMenu: Boolean = false,
    val checkpoints: List<Checkpoint> = emptyList(),
    val lockMenu: Boolean = false,

    val bottomSheet: BottomSheet? = null,
    val drawer: Drawer? = null
)