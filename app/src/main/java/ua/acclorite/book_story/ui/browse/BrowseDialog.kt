/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.core.Dialog
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.browse.model.AddingBooks
import ua.acclorite.book_story.presentation.browse.model.SelectableNullableBook

@Composable
fun BrowseDialog(
    dialog: Dialog?,
    loadingAddDialog: Boolean,
    addingBooks: AddingBooks?,
    selectedBooksAddDialog: List<SelectableNullableBook>,
    dismissAddDialog: (BrowseEvent.OnDismissAddDialog) -> Unit,
    actionAddDialog: (BrowseEvent.OnActionAddDialog) -> Unit,
    cancelAddingBooks: (BrowseEvent.OnCancelAddingBooks) -> Unit,
    selectAddDialog: (BrowseEvent.OnSelectAddDialog) -> Unit
) {
    when (dialog) {
        BrowseScreen.ADD_DIALOG -> {
            BrowseAddDialog(
                loadingAddDialog = loadingAddDialog,
                addingBooks = addingBooks,
                selectedBooksAddDialog = selectedBooksAddDialog,
                dismissAddDialog = dismissAddDialog,
                actionAddDialog = actionAddDialog,
                cancelAddingBooks = cancelAddingBooks,
                selectAddDialog = selectAddDialog
            )
        }
    }
}