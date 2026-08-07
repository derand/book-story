/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.log

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ua.acclorite.book_story.BuildConfig

private const val THEME_TAG = "ThemeTrace"

/**
 * Whether the night mode the app thinks it is in is logged — read with
 * `adb logcat -s ThemeTrace`.
 *
 * On for `debug` and `release-debug`, off for `release`, like [BOOK_TIMING] and
 * for the same reason: `release-debug` is the variant used in daily reading, and
 * this bug takes hours of ordinary use to show up.
 */
const val THEME_TRACE = BuildConfig.THEME_TRACE

/**
 * Logs where the system's night mode stands, from every source that could
 * disagree with the others.
 *
 * A system dark/light switch can be lost: the app keeps drawing the old theme
 * for hours, across foreground/background cycles, until some unrelated
 * configuration change (a rotation) arrives. The platform's own bookkeeping says
 * the new configuration was delivered — both `ActivityRecord` and the window
 * report it — so the disagreement is somewhere inside the process, and only the
 * app can see which layer went stale:
 *
 * - **activity** — `LocalConfiguration` is fed from here, so this is what
 *   `isSystemInDarkTheme` ends up reading;
 * - **app** — the application context's resources, a separate `Configuration`;
 * - **system** — the framework's own resources, updated by `ResourcesManager`;
 * - **setting** — what the user chose, which never goes stale and is here to
 *   date the switch in the log.
 *
 * If the activity disagrees with the other three, a re-read at `ON_START` is
 * enough to fix the bug. If they all agree and the composition still resolved
 * the other way, the staleness is in Compose and the fix has to invalidate it.
 *
 * Costs nothing when [THEME_TRACE] is off: the line is never built.
 */
fun themeTrace(where: String, context: Context) {
    if (!THEME_TRACE) return

    val setting = when (
        (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).nightMode
    ) {
        UiModeManager.MODE_NIGHT_AUTO -> "auto"
        UiModeManager.MODE_NIGHT_NO -> "no"
        UiModeManager.MODE_NIGHT_YES -> "yes"
        else -> "custom"
    }

    logI(
        THEME_TAG,
        "$where: activity=${context.resources.night()} " +
                "app=${context.applicationContext.resources.night()} " +
                "system=${Resources.getSystem().night()} " +
                "setting=$setting"
    )
}

/**
 * Logs what the composition actually resolved, which is the value the user sees.
 * Fires on entering composition and on every later change, so a line here
 * without a matching [themeTrace] line means the app reacted to something other
 * than a lifecycle event.
 *
 * Compiles to nothing when [THEME_TRACE] is off.
 */
@Composable
fun ThemeTraceComposed(isDark: Boolean) {
    if (!THEME_TRACE) return

    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(isDark, systemDark) {
        logI(THEME_TAG, "composed: isDark=$isDark isSystemInDarkTheme=$systemDark")
    }
}

private fun Resources.night(): String {
    return when (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> "dark"
        Configuration.UI_MODE_NIGHT_NO -> "light"
        else -> "undefined"
    }
}
