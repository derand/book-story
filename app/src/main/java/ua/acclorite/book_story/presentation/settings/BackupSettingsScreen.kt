/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import android.os.Parcelable
import androidx.compose.runtime.Composable
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.settings.backup.BackupSettingsCategory
import ua.acclorite.book_story.ui.settings.components.SettingsSubScreen

/**
 * Backing the library up to a file, and restoring it from one.
 *
 * A screen of its own rather than rows in General: this is the first setting
 * that is about the app's data rather than how it behaves, and it must not sit
 * behind the `DB_EXPORT` build flag with "Copy database" — that one is a
 * developer's tool, this one is for everybody.
 */
@Parcelize
object BackupSettingsScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        SettingsSubScreen(title = R.string.backup_settings) {
            BackupSettingsCategory()
        }
    }
}
