/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.general.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.GeneralSettingsEvent
import ua.acclorite.book_story.presentation.settings.GeneralSettingsModel
import ua.acclorite.book_story.ui.settings.components.SettingsActionRow

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
    val generalSettingsModel = hiltViewModel<GeneralSettingsModel>()
    val state = generalSettingsModel.state.collectAsStateWithLifecycle()

    val description = state.value.let { current ->
        when {
            current.databaseCopyError != null -> current.databaseCopyError
            current.databaseCopyPath != null -> current.databaseCopyPath
            else -> stringResource(id = R.string.copy_database_option_desc)
        }
    }

    SettingsActionRow(
        title = stringResource(id = R.string.copy_database_option),
        description = description
    ) {
        generalSettingsModel.onEvent(GeneralSettingsEvent.OnCopyDatabase)
    }
}
