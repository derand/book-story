/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.core.language.Language

@Immutable
sealed class GeneralSettingsEvent {
    data class OnUpdateLanguage(
        val language: Language
    ) : GeneralSettingsEvent()

    data object OnClearParseCache : GeneralSettingsEvent()

    data object OnRefreshParseCacheSize : GeneralSettingsEvent()

    data object OnCopyDatabase : GeneralSettingsEvent()

    data object OnDeleteStatistics : GeneralSettingsEvent()
}
