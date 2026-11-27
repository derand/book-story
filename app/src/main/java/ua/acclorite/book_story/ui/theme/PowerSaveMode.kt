/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether battery saver is on, provided by [BookStoryTheme] from its
 * [rememberPowerSaveMode]. Pure dark's "Saver" mode follows it.
 */
val LocalPowerSaveMode = compositionLocalOf { false }

/**
 * Battery saver as observable state: re-read on every
 * [PowerManager.ACTION_POWER_SAVE_MODE_CHANGED] and on every resume.
 *
 * A plain `isPowerSaveMode` read during composition is not enough: nothing
 * invalidates it, so pure dark kept the state it saw at start until the app was
 * restarted (seen on the tablet, #167). The resume re-read covers a broadcast
 * missed while the receiver was not registered.
 */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val watcher = remember(context) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        PowerSaveModeWatcher { powerManager.isPowerSaveMode }
    }

    DisposableEffect(context, lifecycleOwner, watcher) {
        val unregister = watcher.register(context)
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(watcher)
            unregister()
        }
    }

    return watcher.isPowerSave
}

/**
 * Holds whether battery saver is on, re-reading [readIsPowerSave] on each
 * battery-saver broadcast and on each `ON_RESUME`. Writing back an equal value
 * does not invalidate anything.
 */
internal class PowerSaveModeWatcher(
    private val readIsPowerSave: () -> Boolean
) : BroadcastReceiver(), LifecycleEventObserver {

    var isPowerSave by mutableStateOf(readIsPowerSave())
        private set

    override fun onReceive(context: Context, intent: Intent) {
        refresh()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) refresh()
    }

    fun refresh() {
        isPowerSave = readIsPowerSave()
    }

    /**
     * Registers for the battery-saver broadcast and re-reads once, for a change
     * between construction and registration. Returns the unregister call.
     *
     * Exported on purpose: below API 33 `RECEIVER_NOT_EXPORTED` needs the
     * `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that the manifest removes (it
     * crashed at start on the tablet). The action is a protected broadcast, so
     * only the system can send it either way.
     */
    fun register(context: Context): () -> Unit {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        refresh()
        return { context.unregisterReceiver(this) }
    }
}
