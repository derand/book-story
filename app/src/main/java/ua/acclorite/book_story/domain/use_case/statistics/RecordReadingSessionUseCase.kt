/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.statistics

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
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
     * The session as it was recorded, or null when it was too short to keep —
     * or when statistics are not being collected at all. The caller already
     * treats null as "nothing was banked", so switching collection off needs no
     * second path through the reader.
     */
    suspend operator fun invoke(
        bookId: Int,
        startTime: Long,
        lastActiveTime: Long,
        endTime: Long,
        wordsRead: Int,
        overlayMs: Long
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

        statisticsRepository.addSession(session).fold(
            onSuccess = {
                logI(TAG, "Successfully recorded session for [$bookId].")
            },
            onFailure = {
                logE(TAG, "Could not record session for [$bookId] with error: ${it.message}")
            }
        )

        return session
    }
}
