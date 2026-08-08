/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.ui.text.AnnotatedString
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * How many words this item is worth. Counted from what the reader actually sees,
 * so a chapter heading and a table's cells count while a separator does not, and
 * an image counts only its caption.
 */
fun ReaderText.wordCount(): Int = when (this) {
    is ReaderText.Text -> line.text.countWords()
    is ReaderText.Chapter -> title.countWords()
    is ReaderText.Poem -> lines.sumOf { it.line.text.countWords() }
    is ReaderText.Table -> rows.sumOf { row -> row.sumOf { cell -> cell.text.countWords() } }
    is ReaderText.Image -> caption?.line?.text?.countWords() ?: 0
    ReaderText.Separator -> 0
}

/**
 * How many words a footnote is worth. Notes live outside the text list, so they
 * are counted on their own — never as part of the book's total, which is what
 * coverage is measured against.
 */
fun AnnotatedString.wordCount(): Int = text.countWords()

/**
 * Runs of non-whitespace. Deliberately allocation-free: this runs over every
 * item of the book once, and over the visible ones on every settled scroll.
 */
internal fun String.countWords(): Int {
    var words = 0
    var inWord = false

    for (character in this) {
        if (character.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            words++
        }
    }

    return words
}
