/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Kind of a [ColorPreset]:
 * - [CUSTOM] — user-created; used as-is, never auto-switched.
 * - [DARK]/[LIGHT] — the two built-in presets that ship by default. They are
 *   non-deletable, have fixed names, and (when active) auto-follow the app's
 *   effective dark/light theme.
 */
enum class ColorPresetType { CUSTOM, DARK, LIGHT }

/**
 * The preset actually applied for the current theme. A custom selection is used
 * as-is; a built-in selection auto-follows [isDark] — the Dark built-in when the
 * app is dark, the Light built-in when light. Falls back to [selected] if the
 * matching built-in is missing.
 */
fun List<ColorPreset>.activeColorPreset(selected: ColorPreset, isDark: Boolean): ColorPreset {
    if (!selected.isBuiltIn) return selected
    val wanted = if (isDark) ColorPresetType.DARK else ColorPresetType.LIGHT
    return firstOrNull { it.type == wanted } ?: selected
}

@Immutable
data class ColorPreset(
    val id: Int,
    val name: String,
    val backgroundColor: Color,
    val fontColor: Color,
    val isSelected: Boolean,
    val type: ColorPresetType = ColorPresetType.CUSTOM
) {
    /** A built-in (Dark/Light) preset — non-deletable and theme-aware. */
    val isBuiltIn: Boolean get() = type != ColorPresetType.CUSTOM

    companion object {
        val default = ColorPreset(
            id = -1,
            name = "",
            backgroundColor = Color(0xFFFAF8FF), // Blue Light Surface (hardcoded)
            fontColor = Color(0xFF44464F), // Blue Light OnSurfaceVariant (hardcoded)
            isSelected = false
        )

        /** Factory defaults for the built-in presets (also used by Reset). */
        val builtInDark = ColorPreset(
            id = -1,
            name = "",
            backgroundColor = Color(0xFF000000), // rgb(0, 0, 0)
            fontColor = Color(0xFFCCCCCC), // rgb(204, 204, 204)
            isSelected = false,
            type = ColorPresetType.DARK
        )
        val builtInLight = ColorPreset(
            id = -1,
            name = "",
            backgroundColor = Color(0xFFECE1CA), // rgb(236, 225, 202)
            fontColor = Color(0xFF645032), // rgb(100, 80, 50)
            isSelected = false,
            type = ColorPresetType.LIGHT
        )
    }
}