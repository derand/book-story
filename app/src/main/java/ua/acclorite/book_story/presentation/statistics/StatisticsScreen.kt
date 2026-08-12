/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.statistics

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.navigator.LocalNavigator
import ua.acclorite.book_story.ui.statistics.StatisticsContent

/**
 * Reading across the whole library. Read once on entry: the figures cover
 * everything ever read, so nothing about them changes while the screen is open.
 */
@Parcelize
data object StatisticsScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<StatisticsModel>()
        val navigator = LocalNavigator.current

        var statistics by remember { mutableStateOf<LibraryStatistics?>(null) }

        LaunchedEffect(Unit) {
            statistics = screenModel.load()
        }

        StatisticsContent(
            statistics = statistics,
            navigateBack = { navigator.pop() }
        )
    }
}
