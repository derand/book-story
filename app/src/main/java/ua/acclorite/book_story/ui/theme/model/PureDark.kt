/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme.model

import androidx.annotation.StringRes
import ua.acclorite.book_story.R

enum class PureDark(@StringRes val title: Int) {
    OFF(R.string.pure_dark_off),
    ON(R.string.pure_dark_on),
    SAVER(R.string.pure_dark_power_saver);

    /** [powerSaveMode]: `LocalPowerSaveMode.current` (battery saver as live state). */
    fun isPureDark(powerSaveMode: Boolean): Boolean {
        return when (this) {
            OFF -> false
            ON -> true
            SAVER -> powerSaveMode
        }
    }
}