/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.helpers

import android.content.Context
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.reader.ColorPreset
import ua.acclorite.book_story.domain.model.reader.ColorPresetType

/** Localized name: a built-in by its type, a custom preset by its name or "Preset N". */
fun ColorPreset.title(context: Context): String {
    return when (type) {
        ColorPresetType.DARK -> context.getString(R.string.dark_theme_dark)
        ColorPresetType.LIGHT -> context.getString(R.string.dark_theme_light)
        ColorPresetType.CUSTOM -> name.trim().ifBlank {
            context.getString(R.string.color_preset_query, id.toString())
        }
    }
}
