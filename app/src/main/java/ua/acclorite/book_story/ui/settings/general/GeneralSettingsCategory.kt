/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.general

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.BuildConfig
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.settings.components.SettingsSubcategory
import ua.acclorite.book_story.ui.settings.general.components.AppLanguageOption
import ua.acclorite.book_story.ui.settings.general.components.ClearParseCacheOption
import ua.acclorite.book_story.ui.settings.general.components.CollectStatisticsOption
import ua.acclorite.book_story.ui.settings.general.components.CopyDatabaseOption
import ua.acclorite.book_story.ui.settings.general.components.DeleteStatisticsOption
import ua.acclorite.book_story.ui.settings.general.components.DoublePressExitOption
import ua.acclorite.book_story.ui.settings.general.components.ParseCacheSizeOption

fun LazyListScope.GeneralSettingsCategory(
    titleColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp
) {
    item {
        Spacer(modifier = Modifier.height((topPadding - 8.dp).coerceAtLeast(0.dp)))
    }

    item {
        AppLanguageOption()
    }

    item {
        DoublePressExitOption()
    }

    SettingsSubcategory(
        titleColor = titleColor,
        title = { stringResource(id = R.string.parse_cache_option) },
        showTitle = true,
        showDivider = false
    ) {
        item {
            ParseCacheSizeOption()
        }

        item {
            ClearParseCacheOption()
        }
    }

    SettingsSubcategory(
        titleColor = titleColor,
        title = { stringResource(id = R.string.statistics_option) },
        showTitle = true,
        showDivider = false
    ) {
        item {
            CollectStatisticsOption()
        }

        item {
            DeleteStatisticsOption()
        }
    }

    // Debugging tools, absent from a release build entirely: the reading app is
    // release-debug and is not debuggable, so without this its database cannot
    // be read at all.
    if (BuildConfig.DB_EXPORT) {
        SettingsSubcategory(
            titleColor = titleColor,
            title = { stringResource(id = R.string.debug_option) },
            showTitle = true,
            showDivider = false
        ) {
            item {
                CopyDatabaseOption()
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(bottomPadding))
    }
}