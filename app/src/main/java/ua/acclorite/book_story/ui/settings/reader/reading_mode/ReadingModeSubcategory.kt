/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.reader.reading_mode

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.settings.components.SettingsSubcategory
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.DisableScrollingOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.HorizontalGestureAlphaAnimOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.HorizontalGestureOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.HorizontalGesturePullAnimOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.HorizontalGestureSensitivityOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.PageTurnAnimationOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.PageTurnOverlapOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.TapPagingOption
import ua.acclorite.book_story.ui.settings.reader.reading_mode.components.VolumePagingOption

fun LazyListScope.ReadingModeSubcategory(
    titleColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    title: @Composable () -> String = { stringResource(id = R.string.reading_mode_reader_settings) },
    showTitle: Boolean = true,
    showDivider: Boolean = true
) {
    SettingsSubcategory(
        titleColor = titleColor,
        title = title,
        showTitle = showTitle,
        showDivider = showDivider
    ) {
        item {
            HorizontalGestureOption()
        }

        item {
            TapPagingOption()
        }

        item {
            VolumePagingOption()
        }

        // The overlap, the animation and giving up the scroll describe the page
        // turn itself, so they sit under every trigger that asks for one; what
        // follows belongs to the swipe alone.
        item {
            PageTurnOverlapOption()
        }

        item {
            PageTurnAnimationOption()
        }

        item {
            DisableScrollingOption()
        }

        item {
            HorizontalGestureSensitivityOption()
        }

        item {
            HorizontalGesturePullAnimOption()
        }

        item {
            HorizontalGestureAlphaAnimOption()
        }
    }
}