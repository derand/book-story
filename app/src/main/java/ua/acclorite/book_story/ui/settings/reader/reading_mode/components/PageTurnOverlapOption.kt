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
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.theme.ExpandingTransition

@Composable
fun PageTurnOverlapOption() {
    val settings = LocalSettings.current

    // Shared by both triggers of a page turn, so it stays visible for either.
    ExpandingTransition(visible = settings.pageTurnEnabled) {
        SliderWithTitle(
            value = settings.pageTurnOverlapLines.value to "",
            fromValue = 1,
            toValue = 4,
            title = stringResource(id = R.string.page_turn_overlap_option),
            onValueChange = {
                settings.pageTurnOverlapLines.update(it)
            }
        )
    }
}
