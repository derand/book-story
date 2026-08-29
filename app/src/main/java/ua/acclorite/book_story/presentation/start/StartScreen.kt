/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.start

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.core.data.CoreData
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.navigator.StackEvent
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.ui.common.helpers.LocalActivity
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.model.ListItem
import ua.acclorite.book_story.ui.navigator.LocalNavigator
import ua.acclorite.book_story.ui.start.StartContent

@Parcelize
object StartScreen : Screen, Parcelable {

    @IgnoredOnParcel
    const val SETTINGS = "settings"

    @IgnoredOnParcel
    const val GENERAL_SETTINGS = "general_settings"

    @IgnoredOnParcel
    const val APPEARANCE_SETTINGS = "appearance_settings"

    @IgnoredOnParcel
    const val SCAN_SETTINGS = "scan_settings"

    @IgnoredOnParcel
    const val DONE = "done"

    @SuppressLint("InlinedApi")
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val settingsModel = hiltViewModel<SettingsModel>()
        val settings = LocalSettings.current
        val activity = LocalActivity.current

        val currentPage = remember { mutableIntStateOf(0) }
        val stackEvent = remember { mutableStateOf(StackEvent.DEFAULT) }

        val languages = remember(settings.language.value) {
            CoreData.languages.map { item ->
                ListItem(
                    item = item,
                    title = item.displayName,
                    selected = item == settings.language.lastValue
                )
            }
        }

        StartContent(
            currentPage = currentPage.intValue,
            stackEvent = stackEvent.value,
            languages = languages,
            updateLanguage = { settingsModel.onEvent(SettingsEvent.OnUpdateLanguage(it)) },
            navigateForward = {
                if (currentPage.intValue + 1 == 4) {
                    return@StartContent
                }

                stackEvent.value = StackEvent.DEFAULT
                currentPage.intValue += 1
            },
            navigateBack = {
                if ((currentPage.intValue - 1) < 0) {
                    // Leaving the first page leaves the guide. That is leaving
                    // the app only when the guide *is* the app's first screen;
                    // asked for again from Settings, it has Settings under it.
                    if (navigator.canPop) navigator.pop() else activity.finish()
                } else {
                    stackEvent.value = StackEvent.POP
                    currentPage.intValue -= 1
                }
            },
            navigateToBrowse = {
                settings.showStartScreen.update(false)
                BrowseScreen.refreshListChannel.trySend(Unit)

                // The scan settings the guide just showed are Browse's, so the
                // list is refreshed either way; where "Done" lands is not the
                // same question. A first run has nowhere to go back to and
                // opens the app proper; a re-run returns to Settings.
                if (navigator.canPop) {
                    navigator.pop()
                } else {
                    navigator.push(
                        BrowseScreen,
                        saveInBackStack = false
                    )
                }
            }
        )
    }
}