/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "SaveBookCoverage"

class SaveBookCoverageUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository,
    private val settings: SettingsManager
) {

    /**
     * Coverage is a measurement like any other, so it stops with the rest when
     * collection is off. Reading it back is deliberately *not* gated: what was
     * already measured stays legible, and switching collection back on resumes
     * from it rather than starting the book over.
     */
    suspend operator fun invoke(coverage: ReadingCoverage) {
        if (!settings.collectStatistics.lastValue) return

        statisticsRepository.saveCoverage(coverage).onFailure {
            logW(TAG, "Could not save coverage of [${coverage.bookId}]: ${it.message}")
        }
    }
}
