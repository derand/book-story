/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.document.SEPARATOR_TEXT_REGEX
import ua.acclorite.book_story.data.parser.document.YIELD_INTERVAL
import ua.acclorite.book_story.domain.model.reader.ReaderText
import kotlin.coroutines.coroutineContext

/**
 * The text of a TXT file or of a PDF, one paragraph per line: the first visible
 * line names the chapter as typed, a separator line ("***", "* * *", "---") stays
 * as literal text, as in `.md` and the DOM formats, and every other line goes
 * through [MarkdownParser].
 */
internal suspend fun MarkdownParser.parsePlainLines(lines: Sequence<String>): List<ReaderText> {
    val readerText = mutableListOf<ReaderText>()
    var chapterAdded = false

    lines.forEachIndexed { index, rawLine ->
        coroutineContext.ensureActive()
        if (index % YIELD_INTERVAL == 0) yield()

        val line = rawLine.trim()
        if (!line.containsVisibleText()) return@forEachIndexed

        when {
            SEPARATOR_TEXT_REGEX.matches(line) -> {
                readerText.add(ReaderText.Text(AnnotatedString(line)))
            }

            !chapterAdded -> {
                readerText.add(0, ReaderText.Chapter(title = line, depth = 0))
                chapterAdded = true
            }

            else -> readerText.add(ReaderText.Text(parse(line)))
        }
    }

    return readerText
}
