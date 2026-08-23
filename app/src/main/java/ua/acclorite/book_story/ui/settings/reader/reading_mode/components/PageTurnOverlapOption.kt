/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.reader.reading_mode.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SliderWithTitle
import ua.acclorite.book_story.ui.common.components.settings.lineLabel
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.theme.ExpandingTransition

/** Half a line either way is a visible difference, a whole line a coarse jump. */
private const val STEPS_PER_LINE = 2
private const val MIN_HALF_LINES = 1
private const val MAX_HALF_LINES = 8

@Composable
fun PageTurnOverlapOption() {
    val settings = LocalSettings.current

    // Shared by both triggers of a page turn, so it stays visible for either.
    ExpandingTransition(visible = settings.pageTurnEnabled) {
        SliderWithTitle(
            value = (settings.pageTurnOverlap.value * STEPS_PER_LINE).toInt() to "",
            fromValue = MIN_HALF_LINES,
            toValue = MAX_HALF_LINES,
            title = stringResource(id = R.string.page_turn_overlap_option),
            format = { lineLabel(steps = it, stepsPerLine = STEPS_PER_LINE) },
            onValueChange = { halfLines ->
                settings.pageTurnOverlap.update(halfLines.toFloat() / STEPS_PER_LINE)
            }
        )
    }
}
