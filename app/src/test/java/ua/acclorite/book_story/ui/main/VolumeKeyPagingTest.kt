/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.main

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a volume key means to a reader that has asked for them. */
class VolumeKeyPagingTest {

    private fun outcome(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0
    ) = volumeKeyOutcome(keyCode = keyCode, action = action, repeatCount = repeatCount)

    @Test
    fun aPressOfEitherKeyTurnsAPage() {
        assertEquals(
            VolumeKeyOutcome.Turn(volumeUp = true),
            outcome(KeyEvent.KEYCODE_VOLUME_UP)
        )
        assertEquals(
            VolumeKeyOutcome.Turn(volumeUp = false),
            outcome(KeyEvent.KEYCODE_VOLUME_DOWN)
        )
    }

    @Test
    fun theLiftIsEatenTooOrTheVolumePanelStillAppears() {
        assertEquals(
            VolumeKeyOutcome.Swallow,
            outcome(KeyEvent.KEYCODE_VOLUME_DOWN, action = KeyEvent.ACTION_UP)
        )
    }

    @Test
    fun aHeldKeyTurnsOnePageAndNoMore() {
        // The auto-repeat is consumed rather than acted on: at the repeat rate
        // it would be a scroll through the book, not a page turn.
        assertEquals(
            VolumeKeyOutcome.Swallow,
            outcome(KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 3)
        )
    }

    @Test
    fun everyOtherKeyIsLeftAlone() {
        // The mute key included: it is not a direction, and taking it would
        // leave no way to silence the device from the reader at all.
        assertEquals(VolumeKeyOutcome.Ignore, outcome(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertEquals(VolumeKeyOutcome.Ignore, outcome(KeyEvent.KEYCODE_BACK))
        assertEquals(
            VolumeKeyOutcome.Ignore,
            outcome(KeyEvent.KEYCODE_MEDIA_NEXT, action = KeyEvent.ACTION_UP)
        )
    }
}
