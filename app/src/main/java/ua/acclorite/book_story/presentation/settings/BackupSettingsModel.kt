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
import ua.acclorite.book_story.domain.use_case.backup.ExportBackupUseCase
import javax.inject.Inject

@HiltViewModel
class BackupSettingsModel @Inject constructor(
    private val exportBackupUseCase: ExportBackupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BackupSettingsState())
    val state = _state.asStateFlow()

    fun onEvent(event: BackupSettingsEvent) {
        when (event) {
            is BackupSettingsEvent.OnExport -> {
                if (_state.value.exporting) return
                _state.update { it.copy(exporting = true, exportError = null) }

                viewModelScope.launch {
                    val result = exportBackupUseCase(event.uri)
                    _state.update {
                        it.copy(
                            exporting = false,
                            lastExport = result.getOrNull() ?: it.lastExport,
                            exportError = result.exceptionOrNull()?.let { error ->
                                error.message ?: error.javaClass.simpleName
                            }
                        )
                    }
                }
            }
        }
    }
}
