/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import android.os.Parcelable
import androidx.compose.runtime.Composable
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.settings.browse.BrowseSettingsCategory
import ua.acclorite.book_story.ui.settings.components.SettingsSubScreen

@Parcelize
object BrowseSettingsScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        SettingsSubScreen(title = R.string.browse_settings) {
            BrowseSettingsCategory()
        }
    }
}
