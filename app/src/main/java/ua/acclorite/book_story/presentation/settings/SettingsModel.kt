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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.reader.ColorPreset
import ua.acclorite.book_story.domain.model.reader.ColorPresetType
import ua.acclorite.book_story.domain.model.reader.nextColorPreset
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import ua.acclorite.book_story.domain.use_case.category.AddCategoryUseCase
import ua.acclorite.book_story.domain.use_case.category.DeleteCategoryUseCase
import ua.acclorite.book_story.domain.use_case.category.GetCategoriesUseCase
import ua.acclorite.book_story.domain.use_case.category.UpdateCategoriesOrderUseCase
import ua.acclorite.book_story.domain.use_case.category.UpdateCategoryUseCase
import ua.acclorite.book_story.domain.use_case.cache.ClearParseCacheUseCase
import ua.acclorite.book_story.domain.use_case.cache.GetParseCacheSizeUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.DeleteColorPresetUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.GetColorPresetsUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.ReorderColorPresetsUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.SelectColorPresetUseCase
import ua.acclorite.book_story.domain.use_case.color_preset.UpdateColorPresetUseCase
import ua.acclorite.book_story.domain.use_case.debug.CopyDatabaseUseCase
import ua.acclorite.book_story.domain.use_case.permission.GrantPersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.permission.ReleasePersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.settings.UpdateLanguageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.DeleteStatisticsUseCase
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import javax.inject.Inject
import kotlin.random.Random

private const val TAG = "SettingsModel"

@HiltViewModel
class SettingsModel @Inject constructor(
    private val updateLanguageUseCase: UpdateLanguageUseCase,
    private val getColorPresetsUseCase: GetColorPresetsUseCase,
    private val updateColorPresetUseCase: UpdateColorPresetUseCase,
    private val selectColorPresetUseCase: SelectColorPresetUseCase,
    private val reorderColorPresetsUseCase: ReorderColorPresetsUseCase,
    private val deleteColorPresetUseCase: DeleteColorPresetUseCase,
    private val grantPersistableUriPermissionUseCase: GrantPersistableUriPermissionUseCase,
    private val releasePersistableUriPermissionUseCase: ReleasePersistableUriPermissionUseCase,
    private val addCategoryUseCase: AddCategoryUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val updateCategoriesOrderUseCase: UpdateCategoriesOrderUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val getParseCacheSizeUseCase: GetParseCacheSizeUseCase,
    private val clearParseCacheUseCase: ClearParseCacheUseCase,
    private val copyDatabaseUseCase: CopyDatabaseUseCase,
    private val deleteStatisticsUseCase: DeleteStatisticsUseCase,
    private val getBookSourcesUseCase: GetBookSourcesUseCase
) : ViewModel() {

    /**
     * Held across reading the sources *and* storing them, so two refreshes
     * cannot interleave.
     *
     * `onEvent` launches a coroutine per event and nothing orders them, so
     * removing two folders quickly used to let the earlier refresh finish last
     * and put a row back that was already gone — with a Clear button pointing at
     * a grant that no longer existed.
     */
    private val bookSourcesMutex = Mutex()

    private suspend fun refreshBookSources() = bookSourcesMutex.withLock {
        val sources = getBookSourcesUseCase()
        _state.update { it.copy(bookSources = sources) }
    }

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
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
            var colorPresets = getColorPresetsUseCase()

            // Ensure the two built-in presets (Dark/Light) always exist. Seeded
            // once, non-deletable, and (when active) they auto-follow the theme.
            val wasEmpty = colorPresets.isEmpty()
            var seededAny = false
            // Seed Light before Dark so the built-in chips read Light → Dark,
            // matching the Appearance "Mode" segmented control (System/Light/Dark).
            if (colorPresets.none { it.type == ColorPresetType.LIGHT }) {
                updateColorPresetUseCase(ColorPreset.builtInLight)
                seededAny = true
            }
            if (colorPresets.none { it.type == ColorPresetType.DARK }) {
                updateColorPresetUseCase(ColorPreset.builtInDark)
                seededAny = true
            }
            if (seededAny) colorPresets = getColorPresetsUseCase()

            if (wasEmpty) {
                // Fresh install: default to auto mode (a built-in is selected, so
                // the reading colors follow the app's dark/light theme). If the
                // seeding failed, there is nothing to select: the reader falls
                // back to the default preset and the next start seeds again.
                colorPresets.firstOrNull { it.type == ColorPresetType.DARK }?.let {
                    selectColorPresetUseCase(it)
                    colorPresets = getColorPresetsUseCase()
                }
            }

            val scrollIndex = colorPresets.indexOfFirst { it.isSelected }
            if (scrollIndex != -1) {
                _state.value.colorPresetListState.requestScrollToItem(
                    index = scrollIndex,
                    scrollOffset = 0
                )
            }

            val parseCacheSize = getParseCacheSizeUseCase()
            val categories = getCategoriesUseCase()

            _state.update {
                it.copy(
                    selectedColorPreset = colorPresets.getSelectedColorPreset(),
                    colorPresets = colorPresets,
                    categories = categories,
                    parseCacheSizeBytes = parseCacheSize
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

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnUpdateLanguage -> launchEvent {
                updateLanguageUseCase(event.language)
            }

            is SettingsEvent.OnGrantPersistableUriPermission -> launchEvent {
                sourcesChanged(
                    changed = grantPersistableUriPermissionUseCase(event.uri),
                    added = true
                )
            }

            is SettingsEvent.OnReleasePersistableUriPermission -> launchEvent {
                sourcesChanged(
                    changed = releasePersistableUriPermissionUseCase(event.uri),
                    added = false
                )
            }

            is SettingsEvent.OnRefreshBookSources -> launchEvent {
                refreshBookSources()
            }

            is SettingsEvent.OnCreateCategory -> launchEvent {
                addCategoryUseCase(event.title)
                refreshCategories()
            }

            is SettingsEvent.OnUpdateCategory -> launchEvent {
                updateCategoryUseCase(category = event.category)
                refreshCategories()
            }

            is SettingsEvent.OnUpdateCategoryOrder -> launchEvent {
                updateCategoriesOrderUseCase(categories = event.categories)
                refreshCategories()
            }

            is SettingsEvent.OnRemoveCategory -> launchEvent {
                deleteCategoryUseCase(category = event.category)
                refreshCategories()
            }

            is SettingsEvent.OnSelectColorPreset -> colorPresetTask {
                selectColorPreset(event.id)
            }

            is SettingsEvent.OnSwitchColorPreset -> colorPresetTask {
                switchColorPreset(event.previous)
            }

            is SettingsEvent.OnDeleteColorPreset -> colorPresetTask {
                deleteColorPreset(event.id)
            }

            is SettingsEvent.OnUpdateColorPresetTitle -> colorPresetTask {
                editColorPreset(event.id) { it.copy(name = event.title) }
            }

            is SettingsEvent.OnShuffleColorPreset -> colorPresetTask {
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

            is SettingsEvent.OnAddColorPreset -> colorPresetTask {
                addColorPreset(event.backgroundColor, event.fontColor)
            }

            is SettingsEvent.OnUpdateColorPresetColor -> colorPresetTask {
                editColorPreset(event.id) {
                    it.copy(
                        backgroundColor = event.backgroundColor ?: it.backgroundColor,
                        fontColor = event.fontColor ?: it.fontColor
                    )
                }
            }

            is SettingsEvent.OnResetColorPreset -> colorPresetTask {
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
            is SettingsEvent.OnReorderColorPresets -> _state.update {
                it.copy(colorPresets = it.colorPresets.moved(event.from, event.to))
            }

            is SettingsEvent.OnConfirmReorderColorPresets -> {
                val order = _state.value.colorPresets
                colorPresetTask { reorderColorPresetsUseCase(order) }
            }

            is SettingsEvent.OnClearParseCache -> launchEvent {
                clearParseCacheUseCase()
                val size = getParseCacheSizeUseCase()
                _state.update {
                    it.copy(parseCacheSizeBytes = size)
                }
            }

            is SettingsEvent.OnRefreshParseCacheSize -> launchEvent {
                val size = getParseCacheSizeUseCase()
                _state.update {
                    it.copy(parseCacheSizeBytes = size)
                }
            }

            is SettingsEvent.OnCopyDatabase -> launchEvent {
                val result = copyDatabaseUseCase()
                _state.update {
                    it.copy(
                        databaseCopyPath = result.getOrNull(),
                        databaseCopyError = result.exceptionOrNull()?.message
                    )
                }
            }

            is SettingsEvent.OnDeleteStatistics -> launchEvent {
                deleteStatisticsUseCase()
            }
        }
    }

    /**
     * After a grant was taken or released: Browse walks again only once the
     * grant has changed, so the walk sees the new set of folders, and a failure
     * is said rather than leaving the list silently as it was.
     */
    private suspend fun sourcesChanged(changed: Boolean, added: Boolean) {
        if (changed) {
            BrowseScreen.refreshListChannel.trySend(Unit)
        } else {
            _effects.emit(SettingsEffect.OnSourceChangeFailed(added = added))
        }
        refreshBookSources()
    }

    private fun launchEvent(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun colorPresetTask(task: suspend () -> Unit) {
        colorPresetTasks.trySend(task)
    }

    private suspend fun refreshCategories() {
        val categories = getCategoriesUseCase()
        _state.update { it.copy(categories = categories) }
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

        _effects.emit(SettingsEffect.OnSwitchedColorPreset(newColorPreset))
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
        _state.value.colorPresetListState.requestScrollToItem(
            index = nextPosition,
            scrollOffset = 0
        )
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
        _state.value.colorPresetListState.requestScrollToItem(
            index = colorPresets.lastIndex,
            scrollOffset = 0
        )
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

    private fun SettingsState.withSelected(id: Int): SettingsState {
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
