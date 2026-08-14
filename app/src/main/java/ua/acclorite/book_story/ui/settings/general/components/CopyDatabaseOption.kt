/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.ui.common.components.common.StyledText

/**
 * Copies the database somewhere adb can read it. Present only in the builds
 * whose `DB_EXPORT` flag is on; the reading app is `release-debug`, which is
 * not debuggable, so this is the only way to its data.
 *
 * No confirmation dialog, unlike the destructive rows next to it: this one
 * writes a copy and touches nothing.
 */
@Composable
fun CopyDatabaseOption() {
    val settingsModel = hiltViewModel<SettingsModel>()
    val state = settingsModel.state.collectAsStateWithLifecycle()

    val description = state.value.let { current ->
        when {
            current.databaseCopyError != null -> current.databaseCopyError
            current.databaseCopyPath != null -> current.databaseCopyPath
            else -> stringResource(id = R.string.copy_database_option_desc)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { settingsModel.onEvent(SettingsEvent.OnCopyDatabase) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StyledText(
            text = stringResource(id = R.string.copy_database_option),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        StyledText(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
