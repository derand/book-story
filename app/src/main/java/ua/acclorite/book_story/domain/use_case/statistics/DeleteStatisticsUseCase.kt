/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "DeleteStatistics"

class DeleteStatisticsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /**
     * Erases every measurement: sessions, the record of each book ever read,
     * and the coverage of the books still present.
     *
     * Deliberately unlike deleting a *book*, which anonymises its sessions so
     * the reading still counts towards the lifetime totals. There is no such
     * middle ground here — "delete statistics" can only mean that nothing is
     * left, and a book read to the end will start again from zero.
     */
    suspend operator fun invoke() {
        statisticsRepository.deleteAllStatistics().fold(
            onSuccess = { logI(TAG, "Deleted all statistics.") },
            onFailure = { logW(TAG, "Could not delete statistics: ${it.message}") }
        )
    }
}
