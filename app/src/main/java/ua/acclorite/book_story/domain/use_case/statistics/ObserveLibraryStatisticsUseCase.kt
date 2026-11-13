/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "ObserveLibraryStatistics"

/** [GetLibraryStatisticsUseCase], again after each change to the reading records. */
class ObserveLibraryStatisticsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    operator fun invoke(): Flow<LibraryStatistics> =
        statisticsRepository.observeLibraryStatistics().map { result ->
            result.getOrElse {
                logW(TAG, "Could not read library statistics: ${it.messageForLog()}")
                LibraryStatistics.none
            }
        }
}
