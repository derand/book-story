/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.ChipsWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.model.ListItem

/** Cache size cap presets in MB; 0 disables the parse cache entirely. */
private val PARSE_CACHE_PRESETS_MB = listOf(0, 150, 300, 500, 1000)

@Composable
fun ParseCacheSizeOption() {
    val settings = LocalSettings.current
    val selected = settings.parseCacheSizeMb.value

    ChipsWithTitle(
        title = stringResource(id = R.string.parse_cache_size_option),
        chips = PARSE_CACHE_PRESETS_MB.map { mb ->
            ListItem(
                item = mb,
                title = if (mb == 0) stringResource(id = R.string.parse_cache_off)
                else stringResource(id = R.string.parse_cache_size_value, mb),
                selected = mb == selected
            )
        },
        onClick = { mb ->
            settings.parseCacheSizeMb.update(mb)
        }
    )
}
