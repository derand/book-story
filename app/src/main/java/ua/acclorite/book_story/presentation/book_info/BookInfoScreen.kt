/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.book_info

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.settings.CategoriesModel
import ua.acclorite.book_story.ui.book_info.BookInfoContent
import ua.acclorite.book_story.ui.book_info.BookInfoEffects

@Parcelize
data class BookInfoScreen(
    val bookId: Int,
    /** Open the path dialog on arrival: the reader could not find the book's file. */
    val changePath: Boolean = false
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<BookInfoModel>()
        val categoriesModel = hiltViewModel<CategoriesModel>()

        val allCategories = categoriesModel.categories.collectAsStateWithLifecycle()
        val state = screenModel.state.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()

        val categories = remember(allCategories.value) {
            allCategories.value.filterNot { it.id == -1 }
        }

        // Asked for once. The screen keeps this across a return from a screen
        // pushed over it (Move → "Edit"), when init runs again.
        var pathDialogAsked by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            screenModel.init(
                bookId = bookId,
                changePath = changePath && !pathDialogAsked
            )
            pathDialogAsked = true
        }

        DisposableEffect(Unit) {
            onDispose {
                screenModel.leave()
            }
        }

        BookInfoEffects(
            effects = screenModel.effects,
            screen = this,
            book = state.value.book
        )

        Box(Modifier.fillMaxSize())

        if (state.value.book.id == bookId) {
            BookInfoContent(
                state = state.value,
                categories = categories,
                listState = listState,
                onEvent = screenModel::onEvent
            )
        }
    }
}