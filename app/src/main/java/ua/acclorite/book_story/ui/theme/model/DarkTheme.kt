/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.theme.systemInDarkTheme

enum class DarkTheme(@StringRes val title: Int) {
    FOLLOW_SYSTEM(R.string.dark_theme_follow_system),
    OFF(R.string.dark_theme_light),
    ON(R.string.dark_theme_dark);

    @Composable
    fun isDark(): Boolean {
        return when (this) {
            // Not isSystemInDarkTheme: its LocalConfiguration goes stale — see #50.
            FOLLOW_SYSTEM -> systemInDarkTheme()
            ON -> true
            OFF -> false
        }
    }
}