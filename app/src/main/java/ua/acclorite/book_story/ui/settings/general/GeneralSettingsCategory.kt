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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

fun LazyListScope.GeneralSettingsCategory() {
    item {
        Spacer(modifier = Modifier.height(8.dp))
    }

    item {
        AppLanguageOption()
    }

    item {
        DoublePressExitOption()
    }

    SettingsSubcategory(
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
        Spacer(modifier = Modifier.height(16.dp))
    }
}