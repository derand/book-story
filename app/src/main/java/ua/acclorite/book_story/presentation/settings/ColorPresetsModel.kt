/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.reader.ColorPreset
import ua.acclorite.book_story.domain.model.reader.ColorPresetType
import ua.acclorite.book_story.domain.model.reader.nextColorPreset
import ua.acclorite.book_story.domain.use_case.color_preset.DeleteColorPresetUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.GetColorPresetsUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.ReorderColorPresetsUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.SelectColorPresetUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.UpdateColorPresetUseCase
import javax.inject.Inject
import kotlin.random.Random

private const val TAG = "ColorPresetsModel"

/**
 * The reading colour presets: the list on the Appearance screen, the reader's
 * colours and its fast switch. One instance per activity; the splash screen
 * waits for [initialized].
 */
@HiltViewModel
class ColorPresetsModel @Inject constructor(
    private val getColorPresetsUseCase: GetColorPresetsUseCase,
    private val updateColorPresetUseCase: UpdateColorPresetUseCase,
    private val selectColorPresetUseCase: SelectColorPresetUseCase,
    private val reorderColorPresetsUseCase: ReorderColorPresetsUseCase,
    private val deleteColorPresetUseCase: DeleteColorPresetUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ColorPresetsState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ColorPresetsEffect>()
    val effects = _effects.asSharedFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()

    /**
     * Colour-preset work, run one task at a time in the order the events came
     * in, and only after [init] has loaded the presets.
     *
     * Each event used to join the previous event's job and then launch its own,
     * so two close events could both join the same job and run in parallel:
     * writes landed in any order, and a reorder could be saved without its last
     * step.
     */
    private val colorPresetTasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            // The built-in presets are seeded by the database's open callback.
            val colorPresets = getColorPresetsUseCase()

            _state.update {
                it.copy(
                    selectedColorPreset = colorPresets.getSelectedColorPreset(),
                    colorPresets = colorPresets
                )
            }

            _initialized.update { true }
        }

        viewModelScope.launch {
            _initialized.first { it }
            for (task in colorPresetTasks) {
                runCatchingCancellable { task() }.onFailure {
                    logE(TAG, "Color preset task failed: ${it.messageForLog()}")
                }
            }
        }
    }

    fun onEvent(event: ColorPresetsEvent) {
        when (event) {
            is ColorPresetsEvent.OnSelectColorPreset -> colorPresetTask {
                selectColorPreset(event.id)
            }

            is ColorPresetsEvent.OnSwitchColorPreset -> colorPresetTask {
                switchColorPreset(event.previous)
            }

            is ColorPresetsEvent.OnDeleteColorPreset -> colorPresetTask {
                deleteColorPreset(event.id)
            }

            is ColorPresetsEvent.OnUpdateColorPresetTitle -> colorPresetTask {
                editColorPreset(event.id) { it.copy(name = event.title) }
            }

            is ColorPresetsEvent.OnShuffleColorPreset -> colorPresetTask {
                editColorPreset(event.id) {
                    it.copy(
                        backgroundColor = it.backgroundColor.copy(
                            red = Random.nextFloat(),
                            green = Random.nextFloat(),
                            blue = Random.nextFloat()
                        ),
                        fontColor = it.fontColor.copy(
                            red = Random.nextFloat(),
                            green = Random.nextFloat(),
                            blue = Random.nextFloat()
                        )
                    )
                }
            }

            is ColorPresetsEvent.OnAddColorPreset -> colorPresetTask {
                addColorPreset(event.backgroundColor, event.fontColor)
            }

            is ColorPresetsEvent.OnUpdateColorPresetColor -> colorPresetTask {
                editColorPreset(event.id) {
                    it.copy(
                        backgroundColor = event.backgroundColor ?: it.backgroundColor,
                        fontColor = event.fontColor ?: it.fontColor
                    )
                }
            }

            is ColorPresetsEvent.OnResetColorPreset -> colorPresetTask {
                editColorPreset(event.id) {
                    val defaults = when (it.type) {
                        ColorPresetType.DARK -> ColorPreset.builtInDark
                        ColorPresetType.LIGHT -> ColorPreset.builtInLight
                        ColorPresetType.CUSTOM -> return@editColorPreset null // no reset
                    }
                    it.copy(
                        backgroundColor = defaults.backgroundColor,
                        fontColor = defaults.fontColor
                    )
                }
            }

            // A drag step is applied here and now: the reorderable list expects
            // the items to have moved by the time its onMove returns. Only the
            // finished order is stored, on Confirm.
            is ColorPresetsEvent.OnReorderColorPresets -> _state.update {
                it.copy(colorPresets = it.colorPresets.moved(event.from, event.to))
            }

            is ColorPresetsEvent.OnConfirmReorderColorPresets -> {
                val order = _state.value.colorPresets
                colorPresetTask { reorderColorPresetsUseCase(order) }
            }

        }
    }

    private fun colorPresetTask(task: suspend () -> Unit) {
        colorPresetTasks.trySend(task)
    }

    private suspend fun selectColorPreset(id: Int) {
        val colorPreset = _state.value.colorPresets.getColorPresetById(id) ?: return
        selectColorPresetUseCase(colorPreset)
        _state.update { it.withSelected(colorPreset.id).copy(animateColorPreset = true) }
    }

    private suspend fun switchColorPreset(previous: Boolean) {
        val newColorPreset = _state.value.colorPresets.nextColorPreset(
            selected = _state.value.selectedColorPreset,
            previous = previous
        ) ?: return

        selectColorPresetUseCase(newColorPreset)
        _state.update { it.withSelected(newColorPreset.id).copy(animateColorPreset = false) }

        _effects.emit(ColorPresetsEffect.OnSwitchedColorPreset(newColorPreset))
    }

    private suspend fun deleteColorPreset(id: Int) {
        val colorPresets = _state.value.colorPresets
        if (colorPresets.size == 1) return
        val colorPreset = colorPresets.getColorPresetById(id) ?: return
        val position = colorPresets.indexOf(colorPreset)
        val nextPosition = if (position == colorPresets.lastIndex) position - 1 else position

        deleteColorPresetUseCase(colorPreset)
        val nextColorPreset = getColorPresetsUseCase().getOrNull(nextPosition) ?: return
        selectColorPresetUseCase(nextColorPreset)

        reloadColorPresets()
        _effects.emit(ColorPresetsEffect.OnScrollToColorPreset(nextPosition))
    }

    private suspend fun addColorPreset(backgroundColor: Color, fontColor: Color) {
        updateColorPresetUseCase(
            ColorPreset.default.copy(
                backgroundColor = backgroundColor,
                fontColor = fontColor
            )
        )
        getColorPresetsUseCase().lastOrNull()?.let { selectColorPresetUseCase(it) }

        val colorPresets = reloadColorPresets()
        _effects.emit(ColorPresetsEffect.OnScrollToColorPreset(colorPresets.lastIndex))
    }

    /** Applies [edit] to the stored preset [id]; a `null` from it means no change. */
    private suspend inline fun editColorPreset(
        id: Int,
        edit: (ColorPreset) -> ColorPreset?
    ) {
        val colorPreset = _state.value.colorPresets.getColorPresetById(id) ?: return
        val edited = edit(colorPreset) ?: return
        if (edited == colorPreset) return

        updateColorPresetUseCase(edited)
        _state.update {
            it.copy(
                // Keep the raw selection; in auto mode the edited built-in
                // may differ from the isSelected one.
                selectedColorPreset =
                    if (it.selectedColorPreset.id == edited.id) edited
                    else it.selectedColorPreset,
                colorPresets = it.colorPresets.updateColorPreset(edited)
            )
        }
    }

    private suspend fun reloadColorPresets(): List<ColorPreset> {
        val colorPresets = getColorPresetsUseCase()
        _state.update {
            it.copy(
                selectedColorPreset = colorPresets.getSelectedColorPreset(),
                colorPresets = colorPresets,
                animateColorPreset = false
            )
        }
        return colorPresets
    }

    private fun ColorPresetsState.withSelected(id: Int): ColorPresetsState {
        val colorPresets = colorPresets.map { it.copy(isSelected = it.id == id) }
        return copy(
            selectedColorPreset = colorPresets.getSelectedColorPreset(),
            colorPresets = colorPresets
        )
    }

    private fun List<ColorPreset>.getColorPresetById(id: Int): ColorPreset? {
        return find {
            it.id == id
        }
    }

    private fun List<ColorPreset>.updateColorPreset(colorPreset: ColorPreset): List<ColorPreset> {
        return map {
            if (it.id == colorPreset.id) colorPreset
            else it
        }
    }
}

/** The selected preset; with one preset, that one; with none, the default. */
internal fun List<ColorPreset>.getSelectedColorPreset(): ColorPreset {
    if (size == 1) return first()
    return find { it.isSelected } ?: ColorPreset.default
}

/**
 * The list with the item at [from] moved to [to]; unchanged if either index is
 * out of range (a preset gone between the drag and its step).
 */
internal fun List<ColorPreset>.moved(from: Int, to: Int): List<ColorPreset> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
