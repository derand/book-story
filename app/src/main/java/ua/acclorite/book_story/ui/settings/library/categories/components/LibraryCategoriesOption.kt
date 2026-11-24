/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.library.categories.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableScope
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.settings.CategoriesEvent
import ua.acclorite.book_story.presentation.settings.CategoriesModel
import ua.acclorite.book_story.ui.common.components.common.IconButton
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.dialog.Dialog
import ua.acclorite.book_story.ui.common.components.dialog.DialogWithTextField
import ua.acclorite.book_story.ui.settings.components.SettingsAddRow
import ua.acclorite.book_story.ui.theme.dynamicListItemColor

private const val CREATE_DIALOG = "create_dialog"
private const val EDIT_DIALOG = "edit_dialog"
private const val REMOVE_DIALOG = "remove_dialog"

@Composable
fun LibraryCategoriesOption() {
    val categoriesModel = hiltViewModel<CategoriesModel>()
    val allCategories = categoriesModel.categories.collectAsStateWithLifecycle()

    val categories = remember(allCategories.value) {
        allCategories.value.filterNot { it.id == Category.DEFAULT_ID }
    }
    val dialog = remember {
        mutableStateOf<String?>(null)
    }
    val selectedCategory = remember {
        mutableStateOf<Category?>(null)
    }

    when (dialog.value) {
        CREATE_DIALOG -> {
            LibraryCategoryTitleDialog(
                title = stringResource(id = R.string.create_category),
                initialValue = "",
                onDismiss = { dialog.value = null },
                onAction = { title ->
                    categoriesModel.onEvent(
                        CategoriesEvent.OnCreateCategory(
                            title = title
                        )
                    )
                }
            )
        }

        EDIT_DIALOG -> {
            LibraryCategoryTitleDialog(
                title = stringResource(id = R.string.edit),
                initialValue = selectedCategory.value?.title ?: "",
                onDismiss = { dialog.value = null },
                onAction = { title ->
                    selectedCategory.value?.let { selectedCategory ->
                        categoriesModel.onEvent(
                            CategoriesEvent.OnUpdateCategory(
                                category = selectedCategory.copy(
                                    title = title
                                )
                            )
                        )
                    }
                }
            )
        }

        REMOVE_DIALOG -> {
            LibraryCategoriesRemoveDialog(
                onDismiss = { dialog.value = null },
                onAction = {
                    selectedCategory.value?.let { selectedCategory ->
                        categoriesModel.onEvent(
                            CategoriesEvent.OnRemoveCategory(selectedCategory)
                        )
                    }
                    dialog.value = null
                }
            )
        }
    }

    ReorderableColumn(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        list = categories,
        onSettle = { from, to ->
            categoriesModel.onEvent(
                CategoriesEvent.OnUpdateCategoryOrder(
                    categories.toMutableList().apply {
                        add(to, removeAt(from))
                    }
                )
            )
        }
    ) { _, category, _ ->
        key(category.id) {
            LibraryCategoryItem(
                category = category,
                edit = {
                    selectedCategory.value = category
                    dialog.value = EDIT_DIALOG
                },
                remove = {
                    selectedCategory.value = category
                    dialog.value = REMOVE_DIALOG
                }
            )
        }
    }

    SettingsAddRow(text = stringResource(id = R.string.create_category)) {
        dialog.value = CREATE_DIALOG
    }
}

@Composable
private fun LibraryCategoryTitleDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onAction: (title: String) -> Unit
) {
    DialogWithTextField(
        initialValue = initialValue,
        title = title,
        label = stringResource(id = R.string.library_categories_create_category_placeholder),
        lengthLimit = 200,
        allowBlank = false,
        onDismiss = {
            onDismiss()
        },
        onAction = {
            onAction(it.replace("\n", "").trim())
        }
    )
}

@Composable
private fun LibraryCategoriesRemoveDialog(
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    Dialog(
        title = stringResource(id = R.string.remove_category),
        icon = Icons.AutoMirrored.Outlined.LabelOff,
        description = stringResource(id = R.string.remove_category_description),
        actionEnabled = true,
        onDismiss = {
            onDismiss()
        },
        onAction = {
            onAction()
        },
        withContent = false
    )
}

@Composable
private fun ReorderableScope.LibraryCategoryItem(
    category: Category,
    edit: () -> Unit,
    remove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 18.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = stringResource(id = R.string.drag_content_desc),
            modifier = Modifier
                .clip(CircleShape)
                .draggableHandle()
                .background(MaterialTheme.colorScheme.dynamicListItemColor(category.order))
                .padding(11.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.width(18.dp))

        StyledText(
            text = category.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(18.dp))

        IconButton(
            modifier = Modifier.size(24.dp),
            icon = Icons.Outlined.EditNote,
            contentDescription = R.string.edit_content_desc,
            disableOnClick = false,
            color = MaterialTheme.colorScheme.onSurface
        ) {
            edit()
        }

        IconButton(
            modifier = Modifier.size(24.dp),
            icon = Icons.Outlined.Clear,
            contentDescription = R.string.remove_content_desc,
            disableOnClick = false,
            color = MaterialTheme.colorScheme.onSurface
        ) {
            remove()
        }
    }
}