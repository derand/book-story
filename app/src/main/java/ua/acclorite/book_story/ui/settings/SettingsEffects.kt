/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.reader.activeColorPreset
import ua.acclorite.book_story.presentation.settings.SettingsEffect
import ua.acclorite.book_story.presentation.settings.SettingsState
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.common.helpers.title

/** Needs [LocalSettings]: a built-in preset is named after the theme it is applied in. */
@Composable
fun SettingsEffects(
    effects: SharedFlow<SettingsEffect>,
    state: StateFlow<SettingsState>
) {
    val context = LocalContext.current
    val isDark by rememberUpdatedState(LocalSettings.current.darkTheme.value.isDark())

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SettingsEffect.OnSwitchedColorPreset -> {
                    val applied = state.value.colorPresets.activeColorPreset(
                        selected = effect.newColorPreset,
                        isDark = isDark
                    )
                    context.getString(
                        R.string.color_preset_selected_query,
                        applied.title(context)
                    ).showToast(context, longToast = false)
                }

                // The preset list on the Appearance screen scrolls itself.
                is SettingsEffect.OnScrollToColorPreset -> Unit

                is SettingsEffect.OnSourceChangeFailed -> {
                    context.getString(
                        if (effect.added) R.string.error_could_not_add_folder
                        else R.string.error_could_not_remove_folder
                    ).showToast(context, longToast = false)
                }
            }
        }
    }
}
