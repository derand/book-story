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
fun CollectStatisticsOption() {
    val settings = LocalSettings.current

    SwitchWithTitle(
        selected = settings.collectStatistics.value,
        title = stringResource(id = R.string.collect_statistics_option),
        description = stringResource(id = R.string.collect_statistics_option_desc)
    ) {
        settings.collectStatistics.update(!settings.collectStatistics.lastValue)
    }
}
