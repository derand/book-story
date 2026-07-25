/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

@Composable
fun CacheImagesOption() {
    val settings = LocalSettings.current

    SwitchWithTitle(
        selected = settings.cacheImagesInBooks.value,
        title = stringResource(id = R.string.cache_images_option),
        description = stringResource(id = R.string.cache_images_option_desc)
    ) {
        settings.cacheImagesInBooks.update(!settings.cacheImagesInBooks.lastValue)
    }
}
