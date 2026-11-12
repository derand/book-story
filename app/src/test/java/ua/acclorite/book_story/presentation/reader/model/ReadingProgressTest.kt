/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressTest {

    @Test
    fun `the first item is the start`() {
        assertEquals(0f, readingProgress(0, 5, 100), 0f)
    }

    @Test
    fun `a screen showing the last item is the end`() {
        assertEquals(1f, readingProgress(95, 100, 100), 0f)
    }

    @Test
    fun `otherwise the first visible item's share`() {
        assertEquals(0.25f, readingProgress(25, 30, 100), 0f)
    }

    @Test
    fun `an empty viewport falls back to the first visible item`() {
        assertEquals(0.5f, readingProgress(50, null, 100), 0f)
    }

    @Test
    fun `a one-item book does not divide by zero`() {
        assertEquals(0f, readingProgress(0, null, 0), 0f)
    }
}
