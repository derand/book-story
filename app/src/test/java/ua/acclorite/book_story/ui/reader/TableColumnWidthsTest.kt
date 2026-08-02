/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit-style checks for [resolveColumnWidths], the table column sizer. */
class TableColumnWidthsTest {

    @Test
    fun naturalWidthsThatFitKeepTheirProportionsAndFillTheRow() {
        val widths = resolveColumnWidths(
            natural = intArrayOf(100, 300),
            minimum = intArrayOf(50, 100),
            available = 800
        )

        assertEquals(800, widths.sum())
        // 400 of slack shared 100:300 on top of the natural widths.
        assertArrayEquals(intArrayOf(200, 600), widths)
    }

    @Test
    fun anExactFitIsLeftAlone() {
        val widths = resolveColumnWidths(
            natural = intArrayOf(120, 380),
            minimum = intArrayOf(40, 40),
            available = 500
        )

        assertArrayEquals(intArrayOf(120, 380), widths)
    }

    @Test
    fun tooWideShrinksTheGreedyColumnAndSparesTheNarrowOne() {
        // A one-character index column next to a long description: the index is
        // already at its minimum, so only the description gives width back.
        val widths = resolveColumnWidths(
            natural = intArrayOf(60, 940),
            minimum = intArrayOf(60, 100),
            available = 500
        )

        assertEquals(500, widths.sum())
        assertEquals(60, widths[0])
        assertEquals(440, widths[1])
    }

    @Test
    fun shrinkingSharesTheSqueezeByExcessOverMinimum() {
        // Excess is 300 and 100; 200 of the 400 excess survives, so each column
        // keeps half of what it wanted above its minimum.
        val widths = resolveColumnWidths(
            natural = intArrayOf(400, 200),
            minimum = intArrayOf(100, 100),
            available = 400
        )

        assertArrayEquals(intArrayOf(250, 150), widths)
    }

    @Test
    fun noColumnFallsBelowItsMinimumWhileTheMinimumsStillFit() {
        val natural = intArrayOf(40, 2000, 90)
        val minimum = intArrayOf(40, 60, 90)
        val widths = resolveColumnWidths(natural, minimum, available = 500)

        assertEquals(500, widths.sum())
        for (index in widths.indices) {
            assertTrue(
                "column $index below its minimum: ${widths[index]} < ${minimum[index]}",
                widths[index] >= minimum[index]
            )
        }
    }

    @Test
    fun minimumsThatDoNotFitAreSharedProportionally() {
        val widths = resolveColumnWidths(
            natural = intArrayOf(400, 800),
            minimum = intArrayOf(200, 400),
            available = 300
        )

        assertArrayEquals(intArrayOf(100, 200), widths)
    }

    @Test
    fun roundingLeftoversKeepTheSumExact() {
        val widths = resolveColumnWidths(
            natural = intArrayOf(10, 10, 10),
            minimum = intArrayOf(10, 10, 10),
            available = 1000
        )

        assertEquals(1000, widths.sum())
        // 970 of slack does not divide by three: one column gets the odd pixel.
        assertTrue(widths.contentToString(), widths.all { it == 333 || it == 334 })
    }

    @Test
    fun emptyColumnsFallBackToAnEvenSplit() {
        val widths = resolveColumnWidths(
            natural = intArrayOf(0, 0, 0),
            minimum = intArrayOf(0, 0, 0),
            available = 100
        )

        assertEquals(100, widths.sum())
        assertArrayEquals(intArrayOf(34, 33, 33), widths)
    }

    @Test
    fun degenerateInputsDoNotCrash() {
        assertArrayEquals(intArrayOf(), resolveColumnWidths(intArrayOf(), intArrayOf(), 500))
        assertArrayEquals(
            intArrayOf(0, 0),
            resolveColumnWidths(intArrayOf(10, 10), intArrayOf(5, 5), available = 0)
        )
        assertArrayEquals(
            intArrayOf(0, 0),
            resolveColumnWidths(intArrayOf(10, 10), intArrayOf(5, 5), available = -20)
        )
    }
}
