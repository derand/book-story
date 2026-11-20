/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt

@Composable
fun HighlightedText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    highlightThickness: FontWeight,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val highlightedText = remember(text, highlightThickness) {
        text.withHighlightedReading(highlightThickness)
    }

    BasicText(
        text = highlightedText,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow
    )
}

/**
 * [highlightThickness] laid over the first part of every word ([highlightedReadingRanges]).
 *
 * Only styles are added: the text, and so every offset in it, stays exactly
 * as given. Callers hit-test links against their own string with the layout
 * of this one, and whitespace runs are kept by the parser on purpose.
 *
 * The highlight goes in *before* the text's own spans, so a word that is
 * already bold (or any other weight) keeps its own.
 */
internal fun AnnotatedString.withHighlightedReading(
    highlightThickness: FontWeight
): AnnotatedString {
    val ranges = highlightedReadingRanges(text)
    if (ranges.isEmpty()) return this

    return AnnotatedString.Builder(length).apply {
        ranges.forEach { range ->
            addStyle(SpanStyle(fontWeight = highlightThickness), range.first, range.last + 1)
        }
        append(this@withHighlightedReading)
    }.toAnnotatedString()
}

/**
 * The part of each word that highlighted reading makes heavier: for a word
 * of three letters one letter, otherwise half of them, rounded. Characters
 * that are not letters at either end of the word (quotes, digits,
 * punctuation) are not counted, and those at the start are skipped over.
 * A word with no letters at all gets nothing.
 */
internal fun highlightedReadingRanges(text: String): List<IntRange> =
    WORD.findAll(text).mapNotNull { match ->
        val word = match.value
        val leading = word.indexOfFirst { it.isLetter() }
        if (leading < 0) return@mapNotNull null

        val letters = word.indexOfLast { it.isLetter() } + 1 - leading
        val highlighted = when (letters) {
            3 -> 1
            else -> (letters * 0.5f).roundToInt()
        }
        if (highlighted == 0) return@mapNotNull null

        val start = match.range.first + leading
        start until start + highlighted
    }.toList()

private val WORD = Regex("\\S+")
