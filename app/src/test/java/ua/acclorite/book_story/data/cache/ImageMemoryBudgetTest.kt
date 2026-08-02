/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the tally that decides which images the reader keeps in memory. */
class ImageMemoryBudgetTest {

    @Test
    fun imagesFitUntilTheBudgetIsSpent() {
        val budget = ImageMemoryBudget(maxBytes = 100)

        assertTrue(budget.claim(60))
        assertTrue(budget.claim(40))
        assertFalse(budget.claim(1))
    }

    @Test
    fun anImageThatWouldOverrunIsRefusedWhole() {
        val budget = ImageMemoryBudget(maxBytes = 100)

        assertTrue(budget.claim(90))
        // Refused rather than partly claimed: the caller writes it to a file, so
        // claiming any of it would keep room from an image that does fit.
        assertFalse(budget.claim(20))
        assertTrue(budget.claim(10))
    }

    @Test
    fun oneTooBigDoesNotCloseTheBudget() {
        val budget = ImageMemoryBudget(maxBytes = 100)

        assertFalse(budget.claim(500))
        assertTrue(budget.claim(100))
    }

    @Test
    fun anEmptyBudgetKeepsNothing() {
        val budget = ImageMemoryBudget(maxBytes = 0)

        assertFalse(budget.claim(1))
    }

    @Test
    fun theDefaultIsRoomForAnOrdinaryIllustratedBookAndNoMore() {
        val budget = ImageMemoryBudget()

        // A handful of photographs fits; a manga volume cannot. Sized so the
        // heap-relative term cannot make this depend on the test device.
        assertTrue(budget.claim(1024 * 1024))
        assertFalse(budget.claim(64 * 1024 * 1024))
    }
}
