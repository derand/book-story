/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.presentation.library.LibraryState
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryPage
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.presentation.library.model.pageAt
import ua.acclorite.book_story.ui.theme.DefaultTransition

@Composable
fun LibraryPager(
    state: LibraryState,
    pages: List<LibraryPage>,
    pagerState: PagerState,
    layout: LibraryLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    titlePosition: LibraryTitlePosition,
    readButton: Boolean,
    showProgress: Boolean,
    onEvent: (LibraryEvent) -> Unit
) {
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
        val books = pages.pageAt(index).books

        Box(modifier = Modifier.fillMaxSize()) {
            DefaultTransition(visible = !state.isLoading) {
                LibraryLayout(
                    books = books,
                    gridSize = gridSize,
                    autoGridSize = autoGridSize,
                    layout = layout
                ) { book ->
                    LibraryItem(
                        book = book,
                        layout = layout,
                        hasSelectedItems = state.hasSelectedItems,
                        titlePosition = titlePosition,
                        readButton = readButton,
                        showProgress = showProgress,
                        selectBook = { select ->
                            onEvent(
                                LibraryEvent.OnSelectBook(
                                    id = book.data.id,
                                    select = select
                                )
                            )
                        },
                        navigateToBookInfo = {
                            onEvent(
                                LibraryEvent.OnNavigateToBookInfo(
                                    book.data.id
                                )
                            )
                        },
                        navigateToReader = {
                            onEvent(
                                LibraryEvent.OnNavigateToReader(
                                    book.data.id
                                )
                            )
                        }
                    )
                }
            }

            LibraryEmptyPlaceholder(
                isLoading = state.isLoading,
                isRefreshing = state.isRefreshing,
                isBooksEmpty = books.isEmpty(),
                loadFailed = state.loadFailed,
                onEvent = onEvent
            )
        }
    }
}