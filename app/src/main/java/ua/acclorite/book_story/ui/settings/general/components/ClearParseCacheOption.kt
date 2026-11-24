/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.GeneralSettingsEvent
import ua.acclorite.book_story.presentation.settings.GeneralSettingsModel
import ua.acclorite.book_story.ui.common.components.dialog.Dialog
import ua.acclorite.book_story.ui.settings.components.SettingsActionRow

@Composable
fun ClearParseCacheOption() {
    val generalSettingsModel = hiltViewModel<GeneralSettingsModel>()
    val state = generalSettingsModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    // Read only here, when the row appears: walking the cache at every app
    // start was for a value nobody saw.
    LaunchedEffect(Unit) {
        generalSettingsModel.onEvent(GeneralSettingsEvent.OnRefreshParseCacheSize)
    }

    val sizeText = remember(state.value.parseCacheSizeBytes) {
        Formatter.formatShortFileSize(context, state.value.parseCacheSizeBytes)
    }

    SettingsActionRow(
        title = stringResource(id = R.string.clear_parse_cache_option),
        description = stringResource(id = R.string.clear_parse_cache_option_desc, sizeText)
    ) {
        showDialog = true
    }

    if (showDialog) {
        Dialog(
            title = stringResource(id = R.string.clear_parse_cache_dialog),
            description = stringResource(id = R.string.clear_parse_cache_dialog_desc),
            actionEnabled = true,
            withContent = false,
            onDismiss = { showDialog = false },
            onAction = {
                generalSettingsModel.onEvent(GeneralSettingsEvent.OnClearParseCache)
                showDialog = false
            }
        )
    }
}
