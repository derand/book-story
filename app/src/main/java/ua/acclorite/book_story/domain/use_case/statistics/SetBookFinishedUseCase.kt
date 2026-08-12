/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "SetBookFinished"

class SetBookFinishedUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /**
     * Whether the reader considers this book read. Their statement, not a
     * measurement: people skip appendices, abandon at 97 %, or finish someone
     * else's foreword, so it can be turned off again as freely as on.
     */
    suspend operator fun invoke(book: Book, finished: Boolean) {
        statisticsRepository.setFinished(
            bookId = book.id,
            title = book.title,
            author = book.author.getAsString() ?: "",
            finished = finished
        ).onFailure {
            logW(TAG, "Could not set finished on [${book.id}]: ${it.message}")
        }
    }
}
