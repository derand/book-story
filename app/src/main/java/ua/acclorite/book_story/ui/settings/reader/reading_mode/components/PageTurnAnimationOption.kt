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
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.theme.ExpandingTransition

@Composable
fun PageTurnAnimationOption() {
    val settings = LocalSettings.current

    // Both triggers turn the same page, so the animation is theirs together.
    ExpandingTransition(visible = settings.pageTurnEnabled) {
        SwitchWithTitle(
            selected = settings.pageTurnAnimation.value,
            title = stringResource(id = R.string.page_turn_animation_option),
            description = stringResource(id = R.string.page_turn_animation_option_desc),
            onClick = {
                settings.pageTurnAnimation.update(!settings.pageTurnAnimation.lastValue)
            }
        )
    }
}
