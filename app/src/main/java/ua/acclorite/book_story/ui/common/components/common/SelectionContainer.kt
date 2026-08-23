/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.main.TextActionMode

/**
 * Selection container.
 *
 * The menu itself is Compose's: since Foundation 1.9 a selection no longer goes
 * through [TextToolbar][androidx.compose.ui.platform.TextToolbar] at all
 * (`ComposeFoundationFlags.isNewContextMenuEnabled`), so providing one is a way
 * of being silently ignored. The app's actions are *appended* to the menu
 * Compose builds instead — see [SelectionMenu] for how they reach the selected
 * text.
 *
 * @param onCopyRequested Callback for when text is copied.
 * @param onShareRequested Callback for when the share option is clicked.
 * @param onWebSearchRequested Callback for when the web search option is clicked.
 * @param onTranslateRequested Callback for when the translate option is clicked.
 * @param onDictionaryRequested Callback for when the dictionary option is clicked.
 * @param content Selection container content.
 */
@Composable
fun SelectionContainer(
    onCopyRequested: (() -> Unit),
    onShareRequested: ((String) -> Unit),
    onWebSearchRequested: ((String) -> Unit),
    onTranslateRequested: ((String) -> Unit),
    onDictionaryRequested: ((String) -> Unit),
    content: @Composable (toolbarHidden: Boolean) -> Unit
) {
    val context = LocalContext.current
    val menu = remember { SelectionMenu() }
    menu.onCopied = onCopyRequested

    val clipboard = LocalClipboard.current
    val interceptingClipboard = remember(clipboard, menu) {
        InterceptingClipboard(delegate = clipboard, menu = menu)
    }

    CompositionLocalProvider(LocalClipboard provides interceptingClipboard) {
        SelectionContainer(
            modifier = Modifier
                .appendTextContextMenuComponents {
                    menu.startBuild()

                    separator()
                    item(
                        key = TranslateKey,
                        label = context.getString(R.string.translate)
                    ) {
                        menu.withSelectedText(this, onTranslateRequested)
                    }
                    item(
                        key = ShareKey,
                        label = context.getString(R.string.share)
                    ) {
                        menu.withSelectedText(this, onShareRequested)
                    }
                    item(
                        key = WebSearchKey,
                        label = context.getString(R.string.web_search)
                    ) {
                        menu.withSelectedText(this, onWebSearchRequested)
                    }
                    item(
                        key = DictionaryKey,
                        label = context.getString(R.string.dictionary)
                    ) {
                        menu.withSelectedText(this, onDictionaryRequested)
                    }
                }
                .filterTextContextMenuComponents(menu::keep)
        ) {
            // The menu Compose puts up is a floating action mode, which the window
            // sees; see [TextActionMode] for why the app has to ask the window.
            content(!TextActionMode.active)
        }
    }
}
