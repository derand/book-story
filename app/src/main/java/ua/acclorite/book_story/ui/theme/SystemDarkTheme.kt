/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the system is in dark mode — the replacement for `isSystemInDarkTheme`,
 * which cannot be trusted here.
 *
 * `isSystemInDarkTheme` reads `LocalConfiguration`, and that copy can go stale
 * for hours: when the system switches night mode while the app sits in the
 * background, the activity's resources are updated and `onConfigurationChanged`
 * arrives, but nothing invalidates the composition, so the reader keeps drawing
 * the old theme through any number of resumes. It corrects itself only when an
 * unrelated configuration change — a rotation — happens to come along. See
 * issue #50, where the trace shows every resource reporting light while the
 * composition still resolved dark.
 *
 * So the value is read from the activity's `resources`, which the trace proved
 * correct, and re-read by a [SystemNightModeWatcher] at each of the moments the
 * stale one was missed: every `ON_START`/`ON_RESUME`, and every configuration
 * change Compose *does* see. A missed invalidation then costs one resume rather
 * than lasting until the user happens to rotate the screen.
 */
@Composable
fun systemInDarkTheme(): Boolean {
    val resources = LocalContext.current.resources
    val lifecycleOwner = LocalLifecycleOwner.current

    val watcher = remember(resources) {
        SystemNightModeWatcher { resources.isNightMode() }
    }

    DisposableEffect(lifecycleOwner, watcher) {
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    // The path that already worked — a switch while the app is in the foreground.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration, watcher) { watcher.refresh() }

    return watcher.isNight
}

/**
 * Holds the system's night mode, re-reading [readIsNight] whenever the activity
 * is started or resumed.
 *
 * That re-read is the whole point, and it is not an optimisation to be tidied
 * away later: the value it guards against is one that changed while the app was
 * in the background and that nothing else will announce. Writing the same value
 * back costs nothing — snapshot state only invalidates on a real change — so
 * this does not recompose the app on every resume.
 */
internal class SystemNightModeWatcher(
    private val readIsNight: () -> Boolean
) : LifecycleEventObserver {

    var isNight by mutableStateOf(readIsNight())
        private set

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> refresh()
            else -> Unit
        }
    }

    fun refresh() {
        isNight = readIsNight()
    }
}

private fun Resources.isNightMode(): Boolean {
    return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
