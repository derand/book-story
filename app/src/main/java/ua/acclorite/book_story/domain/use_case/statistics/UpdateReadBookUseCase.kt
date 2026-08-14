/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "UpdateReadBook"

class UpdateReadBookUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /**
     * Folds a session that was actually recorded into the book's lasting record.
     * Called with the same session the statistics kept, so a sitting too short
     * to count does not quietly bump the totals either.
     */
    suspend operator fun invoke(
        session: ReadingSession,
        title: String,
        author: String,
        coveragePercent: Float,
        reachedEnd: Boolean
    ) {
        statisticsRepository.addSessionToReadBook(
            bookId = session.bookId,
            title = title,
            author = author,
            timeMs = session.durationMs,
            words = session.wordsRead,
            endedAt = session.endTime,
            coveragePercent = coveragePercent,
            reachedEnd = reachedEnd
        ).onFailure {
            logW(TAG, "Could not update record of [${session.bookId}]: ${it.message}")
        }
    }
}
