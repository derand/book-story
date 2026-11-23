/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.reader.ColorPreset

@Immutable
sealed class SettingsEffect {
    data class OnSwitchedColorPreset(
        val newColorPreset: ColorPreset
    ) : SettingsEffect()

    /**
     * Bring the preset at [index] into view: after an add (the new last one)
     * or a delete (the one selected in its place). Handled by the preset list.
     */
    data class OnScrollToColorPreset(
        val index: Int
    ) : SettingsEffect()

    /** A folder could not be added ([added]) or removed; nothing changed. */
    data class OnSourceChangeFailed(
        val added: Boolean
    ) : SettingsEffect()
}