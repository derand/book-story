/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.library.display.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.ui.common.components.settings.SegmentedButtonWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.model.ListItem
import ua.acclorite.book_story.ui.theme.ExpandingTransition

@Composable
fun LibraryTitlePositionOption() {
    val settings = LocalSettings.current

    ExpandingTransition(visible = settings.libraryLayout.value == LibraryLayout.GRID) {
        SegmentedButtonWithTitle(
            title = stringResource(id = R.string.title_position_option),
            buttons = LibraryTitlePosition.entries.map { item ->
                ListItem(
                    item = item,
                    title = stringResource(id = item.title),
                    selected = item == settings.libraryTitlePosition.value
                )
            },
            onClick = { item ->
                settings.libraryTitlePosition.update(item)
            }
        )
    }
}