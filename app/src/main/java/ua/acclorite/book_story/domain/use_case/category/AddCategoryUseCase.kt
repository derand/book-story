/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.category

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.domain.repository.CategoryRepository
import javax.inject.Inject

private const val TAG = "AddCategory"

class AddCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {

    suspend operator fun invoke(title: String) {
        logI(TAG, "Inserting a category.")

        categoryRepository.addCategory(Category(title = title)).fold(
            onSuccess = {
                logI(TAG, "Successfully inserted the category.")
            },
            onFailure = {
                logE(TAG, "Could not insert the category with error: ${it.messageForLog()}")
            }
        )
    }
}