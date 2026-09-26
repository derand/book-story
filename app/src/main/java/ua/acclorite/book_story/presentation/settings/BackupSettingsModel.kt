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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.data.backup.RestoreException
import ua.acclorite.book_story.domain.use_case.backup.ConfirmRestoreUseCase
import ua.acclorite.book_story.domain.use_case.backup.DiscardRestoreUseCase
import ua.acclorite.book_story.domain.use_case.backup.ExportBackupUseCase
import ua.acclorite.book_story.domain.use_case.backup.StageRestoreUseCase
import javax.inject.Inject

@HiltViewModel
class BackupSettingsModel @Inject constructor(
    private val exportBackupUseCase: ExportBackupUseCase,
    private val stageRestoreUseCase: StageRestoreUseCase,
    private val discardRestoreUseCase: DiscardRestoreUseCase,
    private val confirmRestoreUseCase: ConfirmRestoreUseCase
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

            is BackupSettingsEvent.OnPickRestore -> {
                if (_state.value.staging || _state.value.stagedRestore != null) return
                _state.update { it.copy(staging = true, restoreError = null) }

                viewModelScope.launch {
                    val result = stageRestoreUseCase(event.uri)
                    _state.update {
                        it.copy(
                            staging = false,
                            stagedRestore = result.getOrNull(),
                            savedBeforeRestore = null,
                            // The dialog shows this one too; one left from the
                            // "Back up" row is not about this restore.
                            exportError = null,
                            restoreError = result.exceptionOrNull()?.let { error ->
                                RestoreFailure(
                                    reason = (error as? RestoreException)?.reason,
                                    detail = error.message ?: error.javaClass.simpleName
                                )
                            }
                        )
                    }
                }
            }

            is BackupSettingsEvent.OnSaveBeforeRestore -> {
                if (_state.value.exporting) return
                _state.update { it.copy(exporting = true, exportError = null) }

                viewModelScope.launch {
                    val result = exportBackupUseCase(event.uri)
                    _state.update {
                        it.copy(
                            exporting = false,
                            savedBeforeRestore = result.getOrNull() ?: it.savedBeforeRestore,
                            exportError = result.exceptionOrNull()?.let { error ->
                                error.message ?: error.javaClass.simpleName
                            }
                        )
                    }
                }
            }

            BackupSettingsEvent.OnCancelRestore -> {
                if (_state.value.restarting) return
                _state.update { it.copy(stagedRestore = null, savedBeforeRestore = null) }
                viewModelScope.launch { discardRestoreUseCase() }
            }

            BackupSettingsEvent.OnConfirmRestore -> {
                val current = _state.value
                if (current.stagedRestore == null || current.exporting || current.restarting) return
                _state.update { it.copy(restarting = true) }

                viewModelScope.launch {
                    runCatching { confirmRestoreUseCase() }.onFailure { error ->
                        _state.update {
                            it.copy(
                                restarting = false,
                                stagedRestore = null,
                                restoreError = RestoreFailure(
                                    reason = null,
                                    detail = error.message ?: error.javaClass.simpleName
                                )
                            )
                        }
                        discardRestoreUseCase()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        // Leaving the screen with the confirmation open is a cancel. A confirmed
        // restore must keep its staging: the process is on its way out.
        // Not in viewModelScope, which is already cancelled by now.
        if (_state.value.stagedRestore != null && !_state.value.restarting) {
            CoroutineScope(Dispatchers.IO).launch { discardRestoreUseCase() }
        }
    }
}
