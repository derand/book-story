/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.statistics.ReadingSession
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import javax.inject.Inject

private const val TAG = "RecordReadingSession"

class RecordReadingSessionUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository,
    private val settings: SettingsManager
) {

    /**
     * Records a session and folds it into the book's record ([title], [author],
     * [coveragePercent] and [reachedEnd] are the record's), both or neither.
     *
     * The session as it was stored, or null when nothing was: too short to
     * keep, statistics not being collected, or the write failed. A session too
     * short to count must not quietly bump the book's totals either, which is
     * why the two writes share this one gate.
     */
    suspend operator fun invoke(
        bookId: Int,
        startTime: Long,
        lastActiveTime: Long,
        endTime: Long,
        wordsRead: Int,
        overlayMs: Long,
        title: String,
        author: String,
        coveragePercent: Float,
        reachedEnd: Boolean
    ): ReadingSession? {
        if (!settings.collectStatistics.lastValue) return null

        val session = ReadingSession.endedAt(
            bookId = bookId,
            startTime = startTime,
            lastActiveTime = lastActiveTime,
            now = endTime,
            wordsRead = wordsRead,
            overlayMs = overlayMs
        )

        if (session == null) {
            logI(TAG, "Session for [$bookId] too short to record.")
            return null
        }

        logI(TAG, "Recording ${session.durationMs}ms session for [$bookId].")

        return statisticsRepository.recordSession(
            session = session,
            title = title,
            author = author,
            coveragePercent = coveragePercent,
            reachedEnd = reachedEnd
        ).fold(
            onSuccess = {
                logI(TAG, "Successfully recorded session for [$bookId].")
                session
            },
            onFailure = {
                logE(TAG, "Could not record session for [$bookId] with error: ${it.messageForLog()}")
                null
            }
        )
    }
}
