/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.statistics.ReadBook
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "GetReadBook"

class GetReadBookUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /** The book's reading record, or null when it has never been read here. */
    suspend operator fun invoke(bookId: Int): ReadBook? =
        statisticsRepository.getReadBook(bookId).getOrElse {
            logW(TAG, "Could not read record of [$bookId]: ${it.message}")
            null
        }
}
