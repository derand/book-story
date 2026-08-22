/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the reader may be a page-only one. */
class ReaderScrollingTest {

    @Test
    fun givingUpTheScrollNeedsSomethingToTurnThePage() {
        assertFalse(readerScrollEnabled(disableScrolling = true, canTurnPage = true))
    }

    @Test
    fun theLastTriggerLeavingBringsTheScrollBack() {
        // Otherwise the text answers to no gesture at all, and the setting that
        // did it is behind a control the reader can no longer see.
        assertTrue(readerScrollEnabled(disableScrolling = true, canTurnPage = false))
    }

    @Test
    fun aReaderWhoNeverAskedKeepsScrolling() {
        assertTrue(readerScrollEnabled(disableScrolling = false, canTurnPage = true))
        assertTrue(readerScrollEnabled(disableScrolling = false, canTurnPage = false))
    }
}
