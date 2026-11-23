/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.library.Category

@Immutable
sealed class CategoriesEvent {
    data class OnCreateCategory(
        val title: String
    ) : CategoriesEvent()

    data class OnUpdateCategory(
        val category: Category
    ) : CategoriesEvent()

    data class OnUpdateCategoryOrder(
        val categories: List<Category>
    ) : CategoriesEvent()

    data class OnRemoveCategory(
        val category: Category
    ) : CategoriesEvent()
}
