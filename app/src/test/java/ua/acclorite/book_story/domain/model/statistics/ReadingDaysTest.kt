/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")
private const val MINUTE = 60_000L

private fun at(date: String, time: String): Long =
    LocalDateTime.parse("${date}T$time").atZone(KYIV).toInstant().toEpochMilli()

class ReadingDaysTest {

    @Test
    fun `an evening's reading is one day`() {
        val shares = ReadingDays.split(
            startTime = at("2026-08-05", "21:00"),
            endTime = at("2026-08-05", "21:40"),
            zone = KYIV
        )

        assertEquals(listOf(ReadingDays.Share(LocalDate.parse("2026-08-05"), 40 * MINUTE)), shares)
    }

    @Test
    fun `reading past midnight counts on both days`() {
        // The point of splitting: a streak must not lose the day you read into.
        val shares = ReadingDays.split(
            startTime = at("2026-08-05", "23:50"),
            endTime = at("2026-08-06", "00:30"),
            zone = KYIV
        )

        assertEquals(
            listOf(
                ReadingDays.Share(LocalDate.parse("2026-08-05"), 10 * MINUTE),
                ReadingDays.Share(LocalDate.parse("2026-08-06"), 30 * MINUTE)
            ),
            shares
        )
    }

    @Test
    fun `a session longer than a day fills the day between`() {
        val shares = ReadingDays.split(
            startTime = at("2026-08-05", "23:00"),
            endTime = at("2026-08-07", "01:00"),
            zone = KYIV
        )

        assertEquals(3, shares.size)
        assertEquals(LocalDate.parse("2026-08-06"), shares[1].day)
        assertEquals(24 * 60 * MINUTE, shares[1].timeMs)
        assertEquals(60 * MINUTE, shares[0].timeMs)
        assertEquals(60 * MINUTE, shares[2].timeMs)
    }

    @Test
    fun `the shares add up to the session`() {
        val start = at("2026-08-05", "22:15")
        val end = at("2026-08-06", "07:45")

        val total = ReadingDays.split(start, end, KYIV).sumOf { it.timeMs }

        assertEquals(end - start, total)
    }

    @Test
    fun `a session ending exactly at midnight stays on its own day`() {
        val shares = ReadingDays.split(
            startTime = at("2026-08-05", "23:30"),
            endTime = at("2026-08-06", "00:00"),
            zone = KYIV
        )

        assertEquals(1, shares.size)
        assertEquals(LocalDate.parse("2026-08-05"), shares.single().day)
    }

    @Test
    fun `an instant session still names its day`() {
        // Zero-length rows should not vanish from the day count.
        val shares = ReadingDays.split(
            startTime = at("2026-08-05", "10:00"),
            endTime = at("2026-08-05", "10:00"),
            zone = KYIV
        )

        assertEquals(listOf(ReadingDays.Share(LocalDate.parse("2026-08-05"), 0)), shares)
    }

    @Test
    fun `days are the reader's own, not UTC`() {
        // 01:30 in Kyiv on the 6th is still the 5th in UTC; the reader was
        // reading on the 6th.
        val day = ReadingDays.dayOf(at("2026-08-06", "01:30"), KYIV)

        assertEquals(LocalDate.parse("2026-08-06"), day)
    }
}
