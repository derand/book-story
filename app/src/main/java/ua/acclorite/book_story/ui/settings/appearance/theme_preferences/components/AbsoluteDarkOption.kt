/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.appearance.theme_preferences.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.theme.ExpandingTransition
import ua.acclorite.book_story.ui.theme.LocalPowerSaveMode

@Composable
fun AbsoluteDarkOption() {
    val settings = LocalSettings.current

    ExpandingTransition(
        visible = settings.pureDark.value.isPureDark(LocalPowerSaveMode.current)
                && settings.darkTheme.value.isDark()
    ) {
        SwitchWithTitle(
            selected = settings.absoluteDark.value,
            title = stringResource(id = R.string.absolute_dark_option),
            description = stringResource(id = R.string.absolute_dark_option_desc),
            onClick = {
                settings.absoluteDark.update(!settings.absoluteDark.lastValue)
            }
        )
    }
}