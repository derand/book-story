/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard

/** Keys of the actions the app adds to the selection menu Compose builds. */
internal data object ShareKey
internal data object WebSearchKey
internal data object TranslateKey
internal data object DictionaryKey

/**
 * The app's own actions inside the selection menu, and the one route to the
 * selected text.
 *
 * Compose builds the menu; the app only appends items to it. Nothing in that API
 * hands over what is selected — the only component that knows is the built-in
 * `Copy`, whose click puts the selection on the clipboard. So the app performs
 * that click and takes the text **in flight**, from a [Clipboard] wrapper
 * ([InterceptingClipboard]) that never passes it on. Sharing a paragraph
 * therefore leaves the user's clipboard exactly as they left it.
 *
 * Everything here is called from the menu's snapshot-aware builder and from its
 * click handlers, all on the main thread; the state is deliberately plain
 * (writing snapshot state while a menu builds is what recomposition loops are
 * made of).
 */
internal class SelectionMenu {

    /** Called for a copy the app did not ask for, i.e. the user's own. */
    var onCopied: () -> Unit = {}

    /**
     * The `Copy` item of the menu currently being built, or `null` while there is
     * nothing selected to copy.
     */
    private var copyItem: TextContextMenuItem? = null

    /** The action waiting for the selection the app just asked Compose to copy. */
    private var awaitingSelection: ((String) -> Unit)? = null

    /**
     * Starts a menu build. Called from the app's builder, which runs after
     * Compose's own (builders run from the bottom of the modifier chain up) and
     * before any filter, so [copyItem] is answered fresh for every menu.
     */
    fun startBuild() {
        copyItem = null
    }

    /**
     * Filters the menu down to the reader's own, and catches
     * [TextContextMenuKeys.CopyKey] on the way past.
     *
     * An allowlist, not a list of exclusions, because the platform's half of the
     * menu cannot be named: `PROCESS_TEXT` items have a public key, but the
     * TextClassifier's smart actions are keyed by a private `Any()` held inside
     * Foundation, so there is nothing to exclude them by. Keeping `Copy` and the
     * app's four is a rule that stays true whatever the platform adds next.
     *
     * What that drops, and why it is no loss:
     * - **`Select all`.** A selection container reaches only what is composed, so
     *   in a lazily laid out book "all" is the page on screen plus whatever is
     *   buffered around it — a selection whose size is an accident of scrolling.
     * - **Other apps' `PROCESS_TEXT` actions and the assistant's suggestions.**
     *   The reader's own `Translate` and `Dictionary` are that same intent
     *   gathered into a chooser, with a fallback when nothing answers it, so
     *   keeping both showed `Translate` twice.
     *
     * The app's own items go too while `Copy` is absent: Compose leaves out an
     * item it would show disabled, so a menu without `Copy` is a menu over an
     * empty selection — which does happen, in the moment a selection is
     * dismissed. There is nothing to share there.
     */
    fun keep(component: TextContextMenuComponent): Boolean {
        val key = component.key
        if (key === TextContextMenuKeys.CopyKey) {
            copyItem = component as? TextContextMenuItem
            return true
        }

        return key in ownKeys && copyItem != null
    }

    /**
     * Runs [action] on the selected text, obtained by asking Compose to copy it
     * and intercepting the clipboard write. Compose starts that write
     * undispatched and it does not suspend before reaching the clipboard, so the
     * text has arrived by the time the click returns; if it ever stops arriving,
     * [action] does not run at all rather than run on somebody else's clipboard.
     */
    fun withSelectedText(session: TextContextMenuSession, action: (String) -> Unit) {
        val copy = copyItem
        if (copy == null) {
            session.close()
            return
        }

        awaitingSelection = action
        try {
            // Closes the menu itself, as every built-in item does.
            copy.onClick(session)
        } finally {
            awaitingSelection = null
        }
    }

    /**
     * Offers a clipboard write to the action waiting for it. Answers whether the
     * write was taken, in which case it must not reach the clipboard.
     */
    fun takeInFlight(text: String?): Boolean {
        val action = awaitingSelection ?: return false
        awaitingSelection = null
        if (text != null) action(text)
        return true
    }

    private val ownKeys = setOf(ShareKey, WebSearchKey, TranslateKey, DictionaryKey)
}

/**
 * The reader's clipboard: the app's own actions take the text they need from it
 * before it is written, and an ordinary copy passes straight through.
 */
internal class InterceptingClipboard(
    private val delegate: Clipboard,
    private val menu: SelectionMenu
) : Clipboard {

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (menu.takeInFlight(clipEntry.plainText())) return

        delegate.setClipEntry(clipEntry)
        menu.onCopied()
    }

    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard
}

private fun ClipEntry?.plainText(): String? {
    val clipData = this?.clipData ?: return null
    if (clipData.itemCount < 1) return null
    return clipData.getItemAt(0)?.text?.toString()
}
