/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.domain.model.statistics.BookStatistics
import ua.acclorite.book_story.domain.model.statistics.ReadingPace
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

class GetBookStatisticsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {

    /**
     * Everything the book's card shows, assembled from its record (time, words,
     * sessions) and its coverage (how much of the text was actually read).
     *
     * "Time left" is worked out from the reader's **typical** pace rather than
     * this book's own, so a book only just started still gets an estimate — and
     * from the words *not covered*, not from where the bookmark sits.
     */
    suspend operator fun invoke(bookId: Int): BookStatistics {
        val record = statisticsRepository.getReadBook(bookId).getOrNull()
        val coverage = statisticsRepository.getCoverage(bookId).getOrNull()

        if (record == null && coverage == null) return BookStatistics.none

        val totalTimeMs = record?.totalTimeMs ?: 0
        val bookWords = coverage?.bookWords ?: 0
        val coveredWords = coverage?.coveredWords ?: 0

        val typical = statisticsRepository.getTypicalWordsPerMinute().getOrNull()

        // A median of this book's own sessions rather than its total words over
        // its total time: one session spent flicking through pages would carry
        // a mean away, and the figure sits right next to the typical pace it is
        // meant to be compared with.
        val inThisBook = statisticsRepository.getTypicalWordsPerMinute(bookId).getOrNull()

        val wordsLeft = (bookWords - coveredWords).coerceAtLeast(0)
        val timeLeftMs = ReadingPace.timeForWords(wordsLeft, typical ?: inThisBook)

        return BookStatistics(
            totalTimeMs = totalTimeMs,
            sessions = record?.sessions ?: 0,
            finished = record?.finished == true,
            coveragePercent = coverage?.percent ?: record?.coveragePercent ?: 0f,
            bookWords = bookWords,
            coveredWords = coveredWords,
            wordsPerMinute = inThisBook,
            typicalWordsPerMinute = typical,
            timeLeftMs = timeLeftMs,
            finishedBy = timeLeftMs?.let {
                ReadingPace.finishedBy(
                    now = System.currentTimeMillis(),
                    timeLeftMs = it,
                    totalTimeMs = totalTimeMs,
                    activeDays = statisticsRepository.countActiveDays(bookId).getOrNull() ?: 0
                )
            }
        )
    }
}
