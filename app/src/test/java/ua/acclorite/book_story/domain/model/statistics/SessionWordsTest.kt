/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionWordsTest {

    @Test
    fun `credits every item once`() {
        val words = SessionWords()

        words.creditItem(index = 1, words = 100)
        words.creditItem(index = 2, words = 50)

        assertEquals(150, words.total)
    }

    @Test
    fun `a screen scrolled back to counts once`() {
        val words = SessionWords()

        words.creditItem(index = 1, words = 100)
        words.creditItem(index = 2, words = 50)
        words.creditItem(index = 1, words = 100)

        assertEquals(150, words.total)
    }

    @Test
    fun `a note counts once however often it is opened`() {
        val words = SessionWords()

        words.creditNote(id = "n1", words = 40)
        words.creditNote(id = "n1", words = 40)

        assertEquals(40, words.total)
    }

    @Test
    fun `notes and items are counted apart`() {
        // An item index and a note id are different namespaces: note "1" must
        // not be swallowed by item 1.
        val words = SessionWords()

        words.creditItem(index = 1, words = 100)
        words.creditNote(id = "1", words = 40)

        assertEquals(140, words.total)
    }

    @Test
    fun `the next session starts from nothing`() {
        val words = SessionWords()
        words.creditItem(index = 1, words = 100)
        words.creditNote(id = "n1", words = 40)

        words.clear()

        assertEquals(0, words.total)

        // And a page genuinely re-read tomorrow is volume read again.
        words.creditItem(index = 1, words = 100)
        words.creditNote(id = "n1", words = 40)

        assertEquals(140, words.total)
    }
}
