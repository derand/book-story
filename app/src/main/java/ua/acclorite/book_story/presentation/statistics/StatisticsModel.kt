/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.statistics

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.domain.use_case.statistics.GetLibraryStatisticsUseCase
import javax.inject.Inject

@HiltViewModel
class StatisticsModel @Inject constructor(
    private val getLibraryStatisticsUseCase: GetLibraryStatisticsUseCase
) : ViewModel() {

    suspend fun load(): LibraryStatistics = getLibraryStatisticsUseCase()
}
