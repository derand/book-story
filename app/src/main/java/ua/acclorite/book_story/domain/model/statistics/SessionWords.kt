/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

/**
 * The words one session has credited: body items by index, footnotes by id.
 *
 * Both are de-duplicated within the session, so scrolling back over a screen or
 * re-opening a note adds nothing the second time. Across sessions they are not:
 * a page genuinely re-read tomorrow is volume read again, which is what volume
 * means.
 */
class SessionWords {

    private val items = mutableSetOf<Int>()
    private val notes = mutableSetOf<String>()

    /** Words credited so far — repeats across sessions included. */
    var total = 0
        private set

    fun creditItem(index: Int, words: Int) {
        if (items.add(index)) total += words
    }

    fun creditNote(id: String, words: Int) {
        if (notes.add(id)) total += words
    }

    fun clear() {
        items.clear()
        notes.clear()
        total = 0
    }
}
