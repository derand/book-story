/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.GeneralSettingsEvent
import ua.acclorite.book_story.presentation.settings.GeneralSettingsModel
import ua.acclorite.book_story.ui.common.components.dialog.Dialog
import ua.acclorite.book_story.ui.settings.components.SettingsActionRow

@Composable
fun DeleteStatisticsOption() {
    val generalSettingsModel = hiltViewModel<GeneralSettingsModel>()

    var showDialog by remember { mutableStateOf(false) }

    SettingsActionRow(
        title = stringResource(id = R.string.delete_statistics_option),
        description = stringResource(id = R.string.delete_statistics_option_desc)
    ) {
        showDialog = true
    }

    if (showDialog) {
        Dialog(
            title = stringResource(id = R.string.delete_statistics_dialog),
            description = stringResource(id = R.string.delete_statistics_dialog_desc),
            actionEnabled = true,
            withContent = false,
            onDismiss = { showDialog = false },
            onAction = {
                generalSettingsModel.onEvent(GeneralSettingsEvent.OnDeleteStatistics)
                showDialog = false
            }
        )
    }
}
