/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.dialog.Dialog

@Composable
fun ClearParseCacheOption() {
    val settingsModel = hiltViewModel<SettingsModel>()
    val state = settingsModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    // The size is loaded once at ViewModel init; refresh it when this screen
    // appears so it reflects books cached since (e.g. after reading).
    LaunchedEffect(Unit) {
        settingsModel.onEvent(SettingsEvent.OnRefreshParseCacheSize)
    }

    val sizeText = remember(state.value.parseCacheSizeBytes) {
        Formatter.formatShortFileSize(context, state.value.parseCacheSizeBytes)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StyledText(
            text = stringResource(id = R.string.clear_parse_cache_option),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        StyledText(
            text = stringResource(id = R.string.clear_parse_cache_option_desc, sizeText),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        Dialog(
            title = stringResource(id = R.string.clear_parse_cache_dialog),
            description = stringResource(id = R.string.clear_parse_cache_dialog_desc),
            actionEnabled = true,
            withContent = false,
            onDismiss = { showDialog = false },
            onAction = {
                settingsModel.onEvent(SettingsEvent.OnClearParseCache)
                showDialog = false
            }
        )
    }
}
