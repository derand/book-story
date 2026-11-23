/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.reader.ColorPreset

@Immutable
data class ColorPresetsState(
    val colorPresets: List<ColorPreset> = emptyList(),
    val selectedColorPreset: ColorPreset = ColorPreset.default,
    val animateColorPreset: Boolean = false
)
