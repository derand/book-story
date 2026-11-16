/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.crash

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import ua.acclorite.book_story.ui.theme.BookStoryTheme
import ua.acclorite.book_story.ui.theme.model.Theme
import ua.acclorite.book_story.ui.theme.model.ThemeContrast

class CrashActivity : AppCompatActivity() {

    companion object {
        /**
         * Set while this screen exists, so that a crash of the screen itself goes
         * to the platform's handler instead of starting the screen again.
         */
        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isRunning = true

        // Default super
        super.onCreate(savedInstanceState)

        // Edge to edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            BookStoryTheme(
                theme = Theme.DYNAMIC,
                isDark = isSystemInDarkTheme(),
                isPureDark = false,
                themeContrast = ThemeContrast.STANDARD
            ) {
                CrashScreen.Content()
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
