/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import android.os.Parcelable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.start.StartScreen
import ua.acclorite.book_story.ui.navigator.LocalNavigator
import ua.acclorite.book_story.ui.settings.SettingsMenu
import ua.acclorite.book_story.ui.settings.components.SettingsSubScreen

@Parcelize
object SettingsScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsSubScreen(
            title = R.string.settings_screen,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            SettingsMenu(
                navigateToGeneralSettings = {
                    navigator.push(GeneralSettingsScreen)
                },
                navigateToAppearanceSettings = {
                    navigator.push(AppearanceSettingsScreen)
                },
                navigateToReaderSettings = {
                    navigator.push(ReaderSettingsScreen)
                },
                navigateToLibrarySettings = {
                    navigator.push(LibrarySettingsScreen)
                },
                navigateToBrowseSettings = {
                    navigator.push(BrowseSettingsScreen)
                },
                navigateToBackupSettings = {
                    navigator.push(BackupSettingsScreen)
                },
                navigateToStart = {
                    // Kept in the back stack, and [showStartScreen] deliberately not
                    // raised: this is a guide the reader asked to see again, not an
                    // app that has yet to be set up. Marking it unconfigured made
                    // every later launch open the guide until it was walked to its
                    // end, and dropping Settings from the stack left no way back.
                    navigator.push(StartScreen)
                }
            )
        }
    }
}
