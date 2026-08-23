/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's own actions inside the selection menu Compose builds: when they are
 * offered at all, and how they reach the selected text without passing it
 * through the user's clipboard.
 */
class SelectionMenuTest {

    private val menu = SelectionMenu()
    private val session = FakeSession()

    /** What Compose adds when there is something to copy. */
    private fun copyItem(onClick: TextContextMenuSession.() -> Unit = {}) =
        TextContextMenuItem(
            key = TextContextMenuKeys.CopyKey,
            label = "Copy",
            leadingIcon = 0,
            onClick = onClick
        )

    private fun ownItem() =
        TextContextMenuItem(key = ShareKey, label = "Share", leadingIcon = 0, onClick = {})

    private fun selectAllItem() =
        TextContextMenuItem(
            key = TextContextMenuKeys.SelectAllKey,
            label = "Select all",
            leadingIcon = 0,
            onClick = {}
        )

    /**
     * Anything the platform put there: another app's PROCESS_TEXT action, or one
     * of the TextClassifier's suggestions. Both are keyed by objects the app
     * cannot name, which is why the menu is filtered by an allowlist.
     */
    private fun foreignItem() =
        TextContextMenuItem(key = Any(), label = "Read aloud", leadingIcon = 0, onClick = {})

    @Test
    fun theAppsActionsAreOfferedWhenThereIsASelection() {
        menu.startBuild()

        assertTrue(menu.keep(copyItem()))
        assertTrue(menu.keep(ownItem()))
    }

    @Test
    fun theAppsActionsAreDroppedWhenNothingIsSelected() {
        // A menu without "Copy" is a menu over an empty selection: Compose leaves
        // out an item it would show disabled. It happens as a selection is
        // dismissed, when the menu is rebuilt carrying "Select all" alone.
        menu.startBuild()

        assertFalse(menu.keep(ownItem()))
    }

    @Test
    fun selectAllGoes() {
        // "All" of a lazily laid out book is the page on screen plus whatever is
        // buffered around it — a number the reader cannot mean.
        menu.startBuild()

        assertFalse(menu.keep(selectAllItem()))
    }

    @Test
    fun whatThePlatformAddedGoesWithIt() {
        // Read aloud, the assistant's suggestions, another app's PROCESS_TEXT
        // action — the reader's own Translate and Dictionary are that same
        // intent, so keeping both showed Translate twice.
        menu.startBuild()
        assertFalse(menu.keep(foreignItem()))

        assertTrue(menu.keep(copyItem()))
        assertFalse(menu.keep(foreignItem()))
    }

    @Test
    fun aBuildDoesNotInheritTheSelectionOfTheOneBefore() {
        menu.startBuild()
        menu.keep(copyItem())

        menu.startBuild()

        assertFalse(menu.keep(ownItem()))
    }

    @Test
    fun theSelectedTextIsTakenInFlightAndNeverReachesTheClipboard() {
        var shared: String? = null
        menu.startBuild()
        // Compose's own copy: a click that writes the selection to the clipboard.
        menu.keep(copyItem { assertTrue(menu.takeInFlight("Кінець секції 10.")) })

        menu.withSelectedText(session) { shared = it }

        assertEquals("Кінець секції 10.", shared)
    }

    @Test
    fun aCopyTheAppDidNotAskForGoesToTheClipboard() {
        menu.startBuild()
        menu.keep(copyItem())

        assertFalse(menu.takeInFlight("Кінець секції 10."))
    }

    @Test
    fun theCopyThatFollowsAShareIsStillTheUsersOwn() {
        var shared: String? = null
        menu.startBuild()
        menu.keep(copyItem { menu.takeInFlight("Кінець секції 10.") })

        menu.withSelectedText(session) { shared = it }

        assertEquals("Кінець секції 10.", shared)
        assertFalse(menu.takeInFlight("Anything the user copies next"))
    }

    @Test
    fun nothingIsSharedWhenTheTextNeverArrives() {
        var shared: String? = null
        menu.startBuild()
        // A copy that writes nothing — the case the interception must not outlive.
        menu.keep(copyItem { })

        menu.withSelectedText(session) { shared = it }

        assertNull(shared)
        assertFalse(menu.takeInFlight("Anything the user copies next"))
    }

    @Test
    fun anActionWithNothingToActOnClosesTheMenu() {
        var shared: String? = null
        menu.startBuild()

        menu.withSelectedText(session) { shared = it }

        assertNull(shared)
        assertTrue(session.closed)
    }

    private class FakeSession : TextContextMenuSession {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
