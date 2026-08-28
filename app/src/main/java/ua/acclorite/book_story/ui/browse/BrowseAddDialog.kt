/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import ua.acclorite.book_story.domain.model.file.SourceLocality
import ua.acclorite.book_story.presentation.browse.BrowseEvent
import ua.acclorite.book_story.presentation.browse.model.AddingBooks
import ua.acclorite.book_story.presentation.browse.model.NullableBook
import ua.acclorite.book_story.presentation.browse.model.SelectableNullableBook
import ua.acclorite.book_story.ui.common.components.dialog.Dialog
import ua.acclorite.book_story.ui.common.components.progress_indicator.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.helpers.showToast

@Composable
fun BrowseAddDialog(
    loadingAddDialog: Boolean,
    addingBooks: AddingBooks?,
    selectedBooksAddDialog: List<SelectableNullableBook>,
    dismissAddDialog: (BrowseEvent.OnDismissAddDialog) -> Unit,
    actionAddDialog: (BrowseEvent.OnActionAddDialog) -> Unit,
    cancelAddingBooks: (BrowseEvent.OnCancelAddingBooks) -> Unit,
    selectAddDialog: (BrowseEvent.OnSelectAddDialog) -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current

    // Shown when at least one of the books being added comes from somewhere the
    // app cannot count on later — a cloud provider, or one that names no
    // location at all. A selection entirely from device storage does not raise
    // the question, so it is not asked, and nothing is copied behind the user's
    // back: when the switch is not there, the add behaves exactly as before.
    val offersCopy = selectedBooksAddDialog.any { selectable ->
        val data = selectable.data
        data is NullableBook.NotNull &&
                SourceLocality.offersLocalCopy(data.book.documentAuthority)
    }

    Dialog(
        title = stringResource(id = R.string.add_books),
        icon = Icons.Default.AddChart,
        description = stringResource(id = R.string.add_books_description),
        actionEnabled = addingBooks == null && !loadingAddDialog &&
                selectedBooksAddDialog.any { it.data is NullableBook.NotNull },
        // While books are being added the buttons have to stay live: Cancel is
        // the only way to stop the work, so the dialog must not disable itself
        // the moment OK is pressed.
        disableOnClick = addingBooks == null,
        onDismiss = {
            if (addingBooks != null) cancelAddingBooks(BrowseEvent.OnCancelAddingBooks)
            else dismissAddDialog(BrowseEvent.OnDismissAddDialog)
        },
        onAction = {
            actionAddDialog(BrowseEvent.OnActionAddDialog)
        },
        withContent = true,
        items = {
            if (addingBooks != null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StyledText(
                            text = stringResource(
                                id = R.string.adding_books_progress,
                                addingBooks.done + 1,
                                addingBooks.total
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StyledText(
                            text = addingBooks.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (addingBooks.total == 0) 0f
                                else addingBooks.done.toFloat() / addingBooks.total
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                return@Dialog
            }

            if (!loadingAddDialog && offersCopy) {
                item {
                    SwitchWithTitle(
                        selected = settings.keepLocalCopy.value,
                        title = stringResource(id = R.string.keep_local_copy_option),
                        description = stringResource(
                            id = R.string.keep_local_copy_option_desc
                        ),
                        horizontalPadding = 0.dp
                    ) {
                        settings.keepLocalCopy.update(!settings.keepLocalCopy.lastValue)
                    }
                }
            }

            if (loadingAddDialog) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    selectedBooksAddDialog,
                    key = { index, _ -> index }
                ) { _, book ->
                    BrowseAddDialogItem(
                        result = book
                    ) {
                        when (book.data) {
                            is NullableBook.NotNull -> {
                                selectAddDialog(
                                    BrowseEvent.OnSelectAddDialog(
                                        book = book
                                    )
                                )
                            }

                            is NullableBook.Null -> {
                                book.data.message?.asString(context)?.showToast(context = context)
                            }
                        }
                    }
                }
            }
        }
    )
}