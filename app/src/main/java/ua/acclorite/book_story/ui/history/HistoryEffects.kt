/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.SharedFlow
import ua.acclorite.book_story.core.log.BookOpenTrace
import ua.acclorite.book_story.presentation.book_info.BookInfoScreen
import ua.acclorite.book_story.presentation.history.HistoryEffect
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.reader.ReaderScreen
import ua.acclorite.book_story.presentation.settings.GeneralSettingsScreen
import ua.acclorite.book_story.presentation.statistics.StatisticsScreen
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Composable
fun HistoryEffects(
    effects: SharedFlow<HistoryEffect>,
    focusRequester: FocusRequester
) {
    val navigator = LocalNavigator.current

    LaunchedEffect(effects, navigator, focusRequester) {
        effects.collect { effect ->
            when (effect) {
                is HistoryEffect.OnRequestFocus -> {
                    focusRequester.requestFocus()
                }

                is HistoryEffect.OnNavigateToLibrary -> {
                    navigator.push(LibraryScreen, saveInBackStack = false)
                }

                is HistoryEffect.OnNavigateToStatistics -> {
                    navigator.push(StatisticsScreen)
                }

                is HistoryEffect.OnNavigateToStatisticsSettings -> {
                    navigator.push(GeneralSettingsScreen)
                }

                is HistoryEffect.OnNavigateToBookInfo -> {
                    navigator.push(BookInfoScreen(bookId = effect.bookId))
                }

                is HistoryEffect.OnNavigateToReader -> {
                    BookOpenTrace.start("book ${effect.bookId} from history")
                    navigator.push(ReaderScreen(bookId = effect.bookId))
                }
            }
        }
    }
}