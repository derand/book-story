/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.main

import android.view.KeyEvent

/**
 * The volume keys, while the reader is asking for them.
 *
 * A hardware key is not a Compose gesture: `onKeyEvent` needs focus, and the
 * reader has none to give — nothing in it is focusable. Volume keys are also
 * handled by the window itself, above the view hierarchy, so the only place
 * early enough to take them is the activity's own
 * [dispatchKeyEvent][android.app.Activity.dispatchKeyEvent]. Hence a listener
 * the reader hangs here while it wants them, and window state, hence a single
 * value for the process: there is exactly one window. The same shape as
 * [TextActionMode], and for the same reason.
 *
 * Nothing listens by default, so with the setting off — and while the menu, a
 * text selection or an overlay is up — the keys are the system's and change the
 * volume, which is the only way to change it while page keys are on.
 */
object VolumeKeyPaging {

    private var listener: ((volumeUp: Boolean) -> Unit)? = null

    /** Takes the keys, until [stopListening] with the same [onVolumeKey]. */
    fun listen(onVolumeKey: (volumeUp: Boolean) -> Unit) {
        listener = onVolumeKey
    }

    /**
     * Gives them back — but only if [onVolumeKey] is still the one holding them.
     * A listener that has already been replaced must not clear its successor:
     * the reader re-registers on a setting change before the old effect is
     * disposed, and the identity check is what keeps that from ending in
     * silence.
     */
    fun stopListening(onVolumeKey: (volumeUp: Boolean) -> Unit) {
        if (listener === onVolumeKey) listener = null
    }

    /** Whether [event] was ours; called from the activity's key dispatch. */
    fun intercept(event: KeyEvent): Boolean {
        val onVolumeKey = listener ?: return false

        return when (val outcome = volumeKeyOutcome(
            keyCode = event.keyCode,
            action = event.action,
            repeatCount = event.repeatCount
        )) {
            is VolumeKeyOutcome.Ignore -> false
            is VolumeKeyOutcome.Swallow -> true
            is VolumeKeyOutcome.Turn -> {
                onVolumeKey(outcome.volumeUp)
                true
            }
        }
    }
}

/** What one key event means to a reader listening for the volume keys. */
internal sealed interface VolumeKeyOutcome {

    /** Not a volume key: it goes on its way untouched. */
    data object Ignore : VolumeKeyOutcome

    /** Ours, but not a page turn — eaten all the same; see [volumeKeyOutcome]. */
    data object Swallow : VolumeKeyOutcome

    /** A page, in the direction [volumeUp] asks for. */
    data class Turn(val volumeUp: Boolean) : VolumeKeyOutcome
}

/**
 * A press turns one page; everything else about the key is swallowed.
 *
 * Both halves matter. The **lift** has to be eaten because the system's volume
 * panel is raised from `ACTION_UP` as much as from the press — consume only the
 * down and the slider still flashes over the text on every page turn. And the
 * auto-**repeat** of a held key is eaten rather than acted on: a key held down
 * would otherwise fly through the book at the repeat rate, which is not a page
 * turn but a scroll nobody asked for. One press, one page.
 */
internal fun volumeKeyOutcome(keyCode: Int, action: Int, repeatCount: Int): VolumeKeyOutcome {
    val volumeUp = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> true
        KeyEvent.KEYCODE_VOLUME_DOWN -> false
        // The mute key is left alone: it is not a direction, so it cannot be a
        // page, and taking it would leave no way to silence the device at all.
        else -> return VolumeKeyOutcome.Ignore
    }

    return when {
        action != KeyEvent.ACTION_DOWN -> VolumeKeyOutcome.Swallow
        repeatCount > 0 -> VolumeKeyOutcome.Swallow
        else -> VolumeKeyOutcome.Turn(volumeUp = volumeUp)
    }
}
