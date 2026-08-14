/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.use_case.book.OpenBookFromUriUseCase
import ua.acclorite.book_story.presentation.main.MainModel
import ua.acclorite.book_story.presentation.reader.ReaderScreen
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * Opens the book behind a file handed over by another app, once it has been
 * read. The wait is shown: a first parse of a book this app has never seen is
 * seconds long, and a tap that appears to do nothing invites a second tap.
 */
@Composable
fun MainFileOpenEffects(mainModel: MainModel) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val opening by mainModel.openingFile.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainModel.fileOpen.collect { state ->
            when (state) {
                is MainModel.FileOpen.Open -> {
                    navigator.push(ReaderScreen(state.bookId))
                }

                is MainModel.FileOpen.Failed -> {
                    val message = when (state.reason) {
                        is OpenBookFromUriUseCase.Result.Unsupported ->
                            R.string.open_file_unsupported

                        else -> R.string.open_file_unreadable
                    }
                    context.getString(message).showToast(context, longToast = true)
                }
            }
        }
    }

    if (opening) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Swallows taps as well as covering: a first parse takes
                // seconds, and the library underneath is still live otherwise.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
