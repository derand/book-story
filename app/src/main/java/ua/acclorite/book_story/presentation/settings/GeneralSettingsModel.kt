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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.domain.use_case.cache.ClearParseCacheUseCase
import ua.acclorite.book_story.domain.use_case.cache.GetParseCacheSizeUseCase
import ua.acclorite.book_story.domain.use_case.debug.CopyDatabaseUseCase
import ua.acclorite.book_story.domain.use_case.settings.UpdateLanguageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.DeleteStatisticsUseCase
import javax.inject.Inject

/**
 * The General settings rows that act rather than store a value: the app
 * language (also set from Start), the parse cache, the debug database copy and
 * deleting the statistics.
 */
@HiltViewModel
class GeneralSettingsModel @Inject constructor(
    private val updateLanguageUseCase: UpdateLanguageUseCase,
    private val getParseCacheSizeUseCase: GetParseCacheSizeUseCase,
    private val clearParseCacheUseCase: ClearParseCacheUseCase,
    private val copyDatabaseUseCase: CopyDatabaseUseCase,
    private val deleteStatisticsUseCase: DeleteStatisticsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GeneralSettingsState())
    val state = _state.asStateFlow()

    fun onEvent(event: GeneralSettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is GeneralSettingsEvent.OnUpdateLanguage -> updateLanguageUseCase(event.language)

                is GeneralSettingsEvent.OnClearParseCache -> {
                    clearParseCacheUseCase()
                    refreshParseCacheSize()
                }

                is GeneralSettingsEvent.OnRefreshParseCacheSize -> refreshParseCacheSize()

                is GeneralSettingsEvent.OnCopyDatabase -> {
                    val result = copyDatabaseUseCase()
                    _state.update {
                        it.copy(
                            databaseCopyPath = result.getOrNull(),
                            databaseCopyError = result.exceptionOrNull()?.message
                        )
                    }
                }

                is GeneralSettingsEvent.OnDeleteStatistics -> deleteStatisticsUseCase()
            }
        }
    }

    private suspend fun refreshParseCacheSize() {
        val size = getParseCacheSizeUseCase()
        _state.update { it.copy(parseCacheSizeBytes = size) }
    }
}
