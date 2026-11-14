/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.ui.common.helpers.LocalActivity
import ua.acclorite.book_story.ui.common.helpers.showToast

@Composable
fun LibraryBackHandler(
    hasSelectedItems: Boolean,
    showSearch: Boolean,
    pagerState: PagerState,
    doublePressExit: Boolean,
    onEvent: (LibraryEvent) -> Unit
) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    // State, not a plain local: a recomposition between the two presses
    // (the list re-reads itself) would otherwise start the count again.
    var shouldExit by remember { mutableStateOf(false) }

    BackHandler {
        if (hasSelectedItems) {
            onEvent(LibraryEvent.OnClearSelectedBooks)
            return@BackHandler
        }

        if (showSearch) {
            onEvent(LibraryEvent.OnSearchVisibility(false))
            return@BackHandler
        }

        if (pagerState.currentPage > 0) {
            scope.launch {
                pagerState.animateScrollToPage(0)
            }
            return@BackHandler
        }

        if (shouldExit || !doublePressExit) {
            activity.finish()
            return@BackHandler
        }

        activity
            .getString(R.string.press_again_toast)
            .showToast(context = activity, longToast = false)
        shouldExit = true

        scope.launch {
            delay(1500)
            shouldExit = false
        }
    }
}