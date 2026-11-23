/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
sealed class ColorPresetsEvent {
    data class OnSelectColorPreset(
        val id: Int,
    ) : ColorPresetsEvent()

    data class OnSwitchColorPreset(
        val previous: Boolean
    ) : ColorPresetsEvent()

    data class OnDeleteColorPreset(
        val id: Int
    ) : ColorPresetsEvent()

    data class OnUpdateColorPresetTitle(
        val id: Int,
        val title: String
    ) : ColorPresetsEvent()

    data class OnShuffleColorPreset(
        val id: Int
    ) : ColorPresetsEvent()

    data class OnAddColorPreset(
        val backgroundColor: Color,
        val fontColor: Color
    ) : ColorPresetsEvent()

    data class OnUpdateColorPresetColor(
        val id: Int,
        val backgroundColor: Color?,
        val fontColor: Color?
    ) : ColorPresetsEvent()

    data class OnResetColorPreset(
        val id: Int
    ) : ColorPresetsEvent()

    data class OnReorderColorPresets(
        val from: Int,
        val to: Int
    ) : ColorPresetsEvent()

    data object OnConfirmReorderColorPresets : ColorPresetsEvent()
}
