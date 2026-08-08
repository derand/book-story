/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.theme

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract behind the fix for issue #50.
 *
 * The system's night mode can change while the app is in the background without
 * anything invalidating the composition, so the app kept drawing the old theme
 * for hours — through repeated resumes — until an unrelated configuration change
 * happened along. These checks pin the property that fixes it: the value is
 * re-read from its source when the activity starts, whether or not anyone
 * announced a change.
 *
 * If someone ever "simplifies" [systemInDarkTheme] back to `isSystemInDarkTheme`,
 * or drops the lifecycle observer as redundant, this test is what fails.
 */
class SystemNightModeWatcherTest {

    /** Drives lifecycle events without a Looper — `createUnsafe` skips the main-thread check. */
    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `takes its initial value from the source`() {
        assertTrue(SystemNightModeWatcher { true }.isNight)
        assertFalse(SystemNightModeWatcher { false }.isNight)
    }

    @Test
    fun `picks up a change that happened while the app was stopped`() {
        var night = false
        val watcher = SystemNightModeWatcher { night }
        val owner = TestOwner()
        owner.registry.addObserver(watcher)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertFalse("nothing changed yet", watcher.isNight)

        // The app goes to the background and the system switches underneath it —
        // silently, which is the whole bug.
        owner.registry.currentState = Lifecycle.State.CREATED
        night = true
        assertFalse("still stopped, nothing has re-read it", watcher.isNight)

        owner.registry.currentState = Lifecycle.State.STARTED
        assertTrue("ON_START must re-read the source", watcher.isNight)
    }

    @Test
    fun `re-reads on every start, in both directions`() {
        var night = true
        val watcher = SystemNightModeWatcher { night }
        val owner = TestOwner()
        owner.registry.addObserver(watcher)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertTrue(watcher.isNight)

        owner.registry.currentState = Lifecycle.State.CREATED
        night = false
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertFalse("dark to light must be picked up too", watcher.isNight)

        owner.registry.currentState = Lifecycle.State.CREATED
        night = true
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertTrue("and back again", watcher.isNight)
    }

    @Test
    fun `refresh re-reads without any lifecycle event`() {
        var night = false
        val watcher = SystemNightModeWatcher { night }

        night = true
        watcher.refresh()

        assertTrue(watcher.isNight)
    }

    @Test
    fun `stopping does not clobber the value`() {
        var night = true
        val watcher = SystemNightModeWatcher { night }
        val owner = TestOwner()
        owner.registry.addObserver(watcher)
        owner.registry.currentState = Lifecycle.State.RESUMED

        // A source that lies once the app is no longer visible must not be read
        // on the way down, only on the way back up.
        night = false
        owner.registry.currentState = Lifecycle.State.CREATED

        assertTrue("ON_PAUSE/ON_STOP must not re-read", watcher.isNight)
    }
}
