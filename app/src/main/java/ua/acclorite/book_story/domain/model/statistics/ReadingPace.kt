/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

/**
 * Turning time and words into a reading pace, and pace back into time.
 *
 * Everything here refuses to answer rather than answer badly: a pace from a
 * handful of seconds is noise, and a "time left" built on noise is worse than
 * no figure at all.
 */
object ReadingPace {

    /** Below this a pace says nothing: one page and a distraction. */
    const val MIN_TIME_MS = 60_000L

    fun wordsPerMinute(timeMs: Long, words: Int): Int? {
        if (timeMs < MIN_TIME_MS || words <= 0) return null
        return (words * 60_000.0 / timeMs).toInt().takeIf { it > 0 }
    }

    /**
     * The **median** of the paces given, not their mean: a device left open on
     * a page produces one absurdly slow session, and a mean would carry it into
     * every figure derived from it.
     */
    fun typical(paces: List<Int>): Int? {
        val sorted = paces.filter { it > 0 }.sorted()
        if (sorted.isEmpty()) return null

        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** How long [words] take at [wordsPerMinute]. */
    fun timeForWords(words: Int, wordsPerMinute: Int?): Long? {
        if (wordsPerMinute == null || wordsPerMinute <= 0 || words <= 0) return null
        return (words * 60_000L) / wordsPerMinute
    }

    /**
     * When the remaining [timeLeftMs] runs out, given how much reading happens
     * on a day when any happens at all. Null when there is not yet a day's worth
     * of evidence.
     */
    fun finishedBy(
        now: Long,
        timeLeftMs: Long,
        totalTimeMs: Long,
        activeDays: Int
    ): Long? {
        if (activeDays <= 0 || totalTimeMs <= 0 || timeLeftMs <= 0) return null

        val perActiveDay = totalTimeMs / activeDays
        if (perActiveDay <= 0) return null

        val days = Math.ceil(timeLeftMs.toDouble() / perActiveDay).toLong()
        return now + days * 24 * 60 * 60 * 1000L
    }
}
