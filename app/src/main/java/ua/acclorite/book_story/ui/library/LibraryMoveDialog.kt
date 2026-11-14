/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.presentation.library.model.DEFAULT_CATEGORY_ID
import ua.acclorite.book_story.presentation.library.model.SelectableBook
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.dialog.CheckboxDialogItem
import ua.acclorite.book_story.ui.common.components.dialog.Dialog

@Composable
fun LibraryMoveDialog(
    books: List<SelectableBook>,
    categories: List<Category>,
    actionMoveDialog: (LibraryEvent.OnActionMoveDialog) -> Unit,
    dismissDialog: (LibraryEvent.OnDismissDialog) -> Unit,
    navigateToLibrarySettings: (LibraryEvent.OnNavigateToLibrarySettings) -> Unit
) {
    val choices = categories.filterNot { it.id == DEFAULT_CATEGORY_ID }
    // Ticked to begin with: the categories every selected book is in already.
    val selectedCategories = remember {
        val selectedBooks = books.filter { it.selected }
        mutableStateListOf<Category>().apply {
            addAll(choices.filter { category ->
                selectedBooks.all { category.id in it.data.categories }
            })
        }
    }

    Dialog(
        title = stringResource(id = R.string.move),
        description = null,
        actionEnabled = true,
        onDismiss = {
            dismissDialog(LibraryEvent.OnDismissDialog)
        },
        onAction = {
            actionMoveDialog(
                LibraryEvent.OnActionMoveDialog(
                    selectedCategories = selectedCategories
                )
            )
        },
        secondaryAction = stringResource(id = R.string.edit),
        onSecondaryAction = {
            navigateToLibrarySettings(LibraryEvent.OnNavigateToLibrarySettings)
            dismissDialog(LibraryEvent.OnDismissDialog)
        },
        withContent = true,
        items = {
            items(choices) { category ->
                val selected = remember(
                    category,
                    selectedCategories
                ) {
                    derivedStateOf {
                        selectedCategories.any { it == category }
                    }
                }

                CheckboxDialogItem(
                    selected = selected.value,
                    title = category.title
                ) {
                    if (selected.value) selectedCategories.remove(category)
                    else selectedCategories.add(category)
                }
            }

            if (choices.isEmpty()) {
                item {
                    StyledText(
                        text = stringResource(id = R.string.categories_empty),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    )
}