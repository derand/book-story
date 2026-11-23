/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.domain.use_case.category.AddCategoryUseCase
import ua.acclorite.book_story.domain.use_case.category.DeleteCategoryUseCase
import ua.acclorite.book_story.domain.use_case.category.GetCategoriesUseCase
import ua.acclorite.book_story.domain.use_case.category.UpdateCategoriesOrderUseCase
import ua.acclorite.book_story.domain.use_case.category.UpdateCategoryUseCase
import javax.inject.Inject

/**
 * The library categories: Library's tabs and filter, Book info's Move, and
 * Library settings. Read once at start and again after each change made here.
 * One instance per activity; the splash screen waits for [initialized], so
 * Library's first frame already has its tabs.
 */
@HiltViewModel
class CategoriesModel @Inject constructor(
    private val addCategoryUseCase: AddCategoryUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val updateCategoriesOrderUseCase: UpdateCategoriesOrderUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()

    init {
        viewModelScope.launch {
            refreshCategories()
            _initialized.value = true
        }
    }

    fun onEvent(event: CategoriesEvent) {
        viewModelScope.launch {
            when (event) {
                is CategoriesEvent.OnCreateCategory -> addCategoryUseCase(event.title)
                is CategoriesEvent.OnUpdateCategory -> updateCategoryUseCase(event.category)
                is CategoriesEvent.OnUpdateCategoryOrder -> updateCategoriesOrderUseCase(event.categories)
                is CategoriesEvent.OnRemoveCategory -> deleteCategoryUseCase(event.category)
            }
            refreshCategories()
        }
    }

    private suspend fun refreshCategories() {
        _categories.value = getCategoriesUseCase()
    }
}
