/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Pure dark's "Saver" mode follows battery saver while the app is open (#167):
 * before, `isPowerSaveMode` was read once during composition and the theme kept
 * that value until the app restarted.
 */
@RunWith(RobolectricTestRunner::class)
class PowerSaveModeWatcherTest {

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `re-reads on the battery saver broadcast`() {
        var saver = false
        val watcher = PowerSaveModeWatcher { saver }
        assertFalse(watcher.isPowerSave)

        saver = true
        watcher.onReceive(context, Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        assertTrue(watcher.isPowerSave)
    }

    @Test
    fun `re-reads on resume`() {
        var saver = false
        val watcher = PowerSaveModeWatcher { saver }
        val owner = TestOwner()
        owner.registry.addObserver(watcher)

        saver = true
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertTrue(watcher.isPowerSave)
    }

    @Test
    fun `registered watcher follows the system broadcast until unregistered`() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val shadowPower = shadowOf(powerManager)
        shadowPower.setIsPowerSaveMode(false)

        val watcher = PowerSaveModeWatcher { powerManager.isPowerSaveMode }
        val unregister = watcher.register(context)

        shadowPower.setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(watcher.isPowerSave)

        unregister()
        shadowPower.setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(watcher.isPowerSave)
    }

    @Test
    fun `register reads a change made before it`() {
        var saver = false
        val watcher = PowerSaveModeWatcher { saver }

        saver = true
        val unregister = watcher.register(context)
        assertTrue(watcher.isPowerSave)
        unregister()
    }
}
