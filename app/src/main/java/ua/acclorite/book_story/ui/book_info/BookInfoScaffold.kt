/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.statistics.BookStatistics
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent

@Composable
fun BookInfoScaffold(
    book: Book,
    file: File?,
    changingLocalCopy: Boolean,
    statistics: BookStatistics?,
    listState: LazyListState,
    onEvent: (BookInfoEvent) -> Unit
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BookInfoTopBar(
                book = book,
                listState = listState,
                onEvent = onEvent
            )
        }
    ) { paddingValues ->
        BookInfoLayout(
            book = book,
            file = file,
            changingLocalCopy = changingLocalCopy,
            statistics = statistics,
            listState = listState,
            paddingValues = paddingValues,
            onEvent = onEvent
        )
    }
}