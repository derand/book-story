/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.reader.chapters.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SliderWithTitle
import ua.acclorite.book_story.ui.common.components.settings.lineLabel
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

/**
 * Quarters of a line, because this slider is judged by eye against running text
 * rather than read off a number: whole lines leave four steps to choose from
 * across the whole range, and even a half-line grid drops the setting the eye
 * asks for when it lands between two of them.
 */
private const val STEPS_PER_LINE = 4

/** No break at all — the chapters run on. */
private const val MIN_QUARTER_LINES = 0

/**
 * Four lines. Where the break stops being a paragraph's worth of air and starts
 * being a gap the eye has to cross, and on a phone that is already a fifth of
 * the screen — past it the setting would be offering a blank page rather than a
 * break.
 */
private const val MAX_QUARTER_LINES = 16

@Composable
fun ChapterBreakOption() {
    val settings = LocalSettings.current

    SliderWithTitle(
        value = (settings.chapterBreak.value * STEPS_PER_LINE).toInt() to "",
        fromValue = MIN_QUARTER_LINES,
        toValue = MAX_QUARTER_LINES,
        title = stringResource(id = R.string.chapter_break_option),
        format = { lineLabel(steps = it, stepsPerLine = STEPS_PER_LINE) },
        onValueChange = { quarterLines ->
            settings.chapterBreak.update(quarterLines.toFloat() / STEPS_PER_LINE)
        }
    )
}
