/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import ua.acclorite.book_story.domain.model.reader.SearchMatch

/**
 * The one the arrows are pointing at, and the rest that happen to be on the
 * same screen. Both are tints of the page's own font colour rather than a fixed
 * highlight: the reader's colours come from a preset the user may define
 * freely, so a hardcoded yellow would be a stranger on half of them — while the
 * font colour is the one colour guaranteed to contrast with the background.
 */
private const val CURRENT_MATCH_ALPHA = 0.32f
private const val OTHER_MATCH_ALPHA = 0.12f

/**
 * Lays the search highlights over one rendered string — the paragraph's line, a
 * poem's line, a table cell, a caption, a chapter title.
 *
 * Applied here, at render time, and never to the stored text: the parsed
 * [AnnotatedString]s are shared and cached on disk, and a search is not
 * something a book should be remembered by.
 */
fun AnnotatedString.withSearchHighlights(
    matches: List<SearchMatch>,
    current: SearchMatch?,
    fontColor: Color,
    part: Int = 0
): AnnotatedString {
    val mine = matches.filter { it.part == part }
    if (mine.isEmpty()) return this

    return AnnotatedString.Builder(this).apply {
        mine.forEach { match ->
            val start = match.start.coerceIn(0, length)
            val end = match.end.coerceIn(start, length)
            if (start == end) return@forEach

            addStyle(
                style = SpanStyle(
                    background = fontColor.copy(
                        alpha = when (match) {
                            current -> CURRENT_MATCH_ALPHA
                            else -> OTHER_MATCH_ALPHA
                        }
                    )
                ),
                start = start,
                end = end
            )
        }
    }.toAnnotatedString()
}
