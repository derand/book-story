/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which local days a stretch of reading belongs to.
 *
 * A sitting from 23:50 to 00:30 is reading on **both** days, so it is split at
 * midnight rather than filed under the day it started. Days are the reader's
 * own, not UTC: a day ends when their night does.
 *
 * Nothing here is stored — days are worked out from the session rows when they
 * are read, so the rows stay plain facts and a change of mind about boundaries
 * costs nothing.
 */
object ReadingDays {

    /** A day, and how much of a session fell inside it. */
    data class Share(
        val day: LocalDate,
        val timeMs: Long
    )

    /**
     * The days [startTime]..[endTime] covers, in order, each with its share of
     * the time. A session that spans a whole day contributes that whole day.
     */
    fun split(startTime: Long, endTime: Long, zone: ZoneId): List<Share> {
        if (endTime <= startTime) {
            return listOf(Share(dayOf(startTime, zone), 0))
        }

        val shares = mutableListOf<Share>()
        var cursor = startTime

        while (cursor < endTime) {
            val day = dayOf(cursor, zone)
            val nextMidnight = day.plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val until = minOf(nextMidnight, endTime)

            shares.add(Share(day, until - cursor))
            cursor = until
        }

        return shares
    }

    fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
