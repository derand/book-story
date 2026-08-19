/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether a floating text-selection toolbar is on screen.
 *
 * The reader has to know: while text is selected, a tap belongs to the
 * selection — it dismisses it — and must not turn a page, open the menu or
 * follow a link. The app's own [TextToolbar][androidx.compose.ui.platform.TextToolbar]
 * cannot answer that question. It is bypassed on at least some devices, where
 * the platform puts up its own floating toolbar instead and the app's
 * `TextToolbarStatus` stays `Hidden` for the whole life of the selection.
 *
 * A window-level action mode is the one signal that holds whoever shows the
 * menu, so it is taken from the activity's own callbacks. Window state, hence a
 * single value for the process: there is exactly one window.
 */
object TextActionMode {

    var active by mutableStateOf(false)
        internal set
}
