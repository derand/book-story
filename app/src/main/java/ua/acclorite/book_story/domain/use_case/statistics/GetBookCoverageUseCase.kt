/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "GetBookCoverage"

class GetBookCoverageUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /**
     * What of this book has been read, ready for a text of [itemCount] items:
     * a stale record keeps its word total but drops its intervals, and a book
     * never read yet starts empty.
     */
    suspend operator fun invoke(
        bookId: Int,
        itemCount: Int,
        bookWords: Int
    ): ReadingCoverage {
        val stored = statisticsRepository.getCoverage(bookId).getOrElse {
            logW(TAG, "Could not read coverage of [$bookId]: ${it.message}")
            null
        } ?: return ReadingCoverage.empty(bookId, itemCount, bookWords)

        if (stored.itemCount != itemCount) {
            logI(
                TAG,
                "Coverage of [$bookId] was recorded against ${stored.itemCount} " +
                        "items, text now has $itemCount — dropping its intervals."
            )
        }

        return stored.validFor(itemCount)
    }
}
