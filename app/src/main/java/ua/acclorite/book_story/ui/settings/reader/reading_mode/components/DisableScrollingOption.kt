/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.reader.reading_mode.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.theme.ExpandingTransition

@Composable
fun DisableScrollingOption() {
    val settings = LocalSettings.current

    // What this gives up free scrolling for is the page turn, so it is offered
    // to whoever can turn a page rather than to one gesture that can.
    ExpandingTransition(visible = settings.pageTurnEnabled) {
        SwitchWithTitle(
            selected = settings.disableScrolling.value,
            title = stringResource(id = R.string.horizontal_gesture_disable_scrolling_option),
            description = stringResource(
                id = R.string.horizontal_gesture_disable_scrolling_option_desc
            ),
            onClick = {
                settings.disableScrolling.update(!settings.disableScrolling.lastValue)
            }
        )
    }
}
