/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.domain.model.reader.ColorPreset

@Immutable
data class SettingsState(
    val colorPresets: List<ColorPreset> = emptyList(),
    val selectedColorPreset: ColorPreset = ColorPreset.default,
    val animateColorPreset: Boolean = false,
    val colorPresetListState: LazyListState = LazyListState(),

    val categories: List<Category> = emptyList(),

    val parseCacheSizeBytes: Long = 0L,

    /**
     * The places books are read from, each with whether it answered when it was
     * last asked. Read when the Browse settings open and after any grant
     * changes; a source going quiet in between is what the next open reports.
     */
    val bookSources: List<BookSource> = emptyList(),

    /**
     * Where the last "Copy database" landed, or why it did not. Both null until
     * the row is tapped; a copy that reported nothing would leave the next
     * question to be answered from a stale file.
     */
    val databaseCopyPath: String? = null,
    val databaseCopyError: String? = null
)