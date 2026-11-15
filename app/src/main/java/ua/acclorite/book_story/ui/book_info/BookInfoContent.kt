/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.presentation.book_info.BookInfoState

@Composable
fun BookInfoContent(
    state: BookInfoState,
    categories: List<Category>,
    listState: LazyListState,
    onEvent: (BookInfoEvent) -> Unit
) {
    BookInfoDialog(
        dialog = state.dialog,
        book = state.book,
        categories = categories,
        onEvent = onEvent
    )

    BookInfoBottomSheet(
        bottomSheet = state.bottomSheet,
        book = state.book,
        file = state.file,
        loadingFile = state.loadingFile,
        canResetCover = state.canResetCover,
        onEvent = onEvent
    )

    BookInfoScaffold(
        book = state.book,
        file = state.file,
        changingLocalCopy = state.changingLocalCopy,
        statistics = state.statistics,
        listState = listState,
        onEvent = onEvent
    )

    BookInfoBackHandler(
        onEvent = onEvent
    )
}
