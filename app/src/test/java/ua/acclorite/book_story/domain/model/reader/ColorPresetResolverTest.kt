/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit-style checks for [activeColorPreset] (the theme-aware resolver). */
class ColorPresetResolverTest {

    private val dark = ColorPreset(
        id = 1, name = "", backgroundColor = Color.Black, fontColor = Color.White,
        isSelected = false, type = ColorPresetType.DARK
    )
    private val light = ColorPreset(
        id = 2, name = "", backgroundColor = Color.White, fontColor = Color.Black,
        isSelected = false, type = ColorPresetType.LIGHT
    )
    private val custom = ColorPreset(
        id = 3, name = "Sepia", backgroundColor = Color.Yellow, fontColor = Color.Red,
        isSelected = true, type = ColorPresetType.CUSTOM
    )
    private val presets = listOf(dark, light, custom)

    @Test
    fun customSelectedIsUsedAsIs() {
        assertEquals(custom, presets.activeColorPreset(selected = custom, isDark = true))
        assertEquals(custom, presets.activeColorPreset(selected = custom, isDark = false))
    }

    @Test
    fun builtInFollowsTheme() {
        // Which built-in is "selected" is irrelevant — the theme decides.
        assertEquals(dark, presets.activeColorPreset(selected = light, isDark = true))
        assertEquals(light, presets.activeColorPreset(selected = dark, isDark = false))
        assertEquals(dark, presets.activeColorPreset(selected = dark, isDark = true))
        assertEquals(light, presets.activeColorPreset(selected = light, isDark = false))
    }

    @Test
    fun fallsBackToSelectedWhenMatchMissing() {
        val onlyLight = listOf(light, custom)
        // No Dark built-in present → keep the (built-in) selection.
        assertEquals(dark, onlyLight.activeColorPreset(selected = dark, isDark = true))
    }
}
