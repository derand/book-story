/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader's content padding: a system bar wins on its own side, the cutout
 * counts only where there is no bar — not the larger of the two.
 */
class ContentPaddingTest {

    private val cutout = PaddingValues(start = 30.dp, top = 40.dp, end = 0.dp, bottom = 5.dp)

    @Test
    fun `a side with a bar keeps the bar even when the cutout is deeper`() {
        val bars = PaddingValues(start = 0.dp, top = 24.dp, end = 0.dp, bottom = 48.dp)

        val padding = bars.orWhereZero(cutout, LayoutDirection.Ltr)

        assertEquals(
            PaddingValues(start = 30.dp, top = 24.dp, end = 0.dp, bottom = 48.dp),
            padding
        )
    }

    @Test
    fun `with no bars the cutout is used as is`() {
        val padding = PaddingValues(0.dp).orWhereZero(cutout, LayoutDirection.Ltr)

        assertEquals(cutout, padding)
    }

    @Test
    fun `start and end follow the layout direction`() {
        val bars = PaddingValues(start = 0.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)

        val padding = bars.orWhereZero(cutout, LayoutDirection.Rtl)

        assertEquals(30.dp, padding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(16.dp, padding.calculateEndPadding(LayoutDirection.Rtl))
    }
}
