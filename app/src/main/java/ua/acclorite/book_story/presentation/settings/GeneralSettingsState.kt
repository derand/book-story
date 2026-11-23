/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable

@Immutable
data class GeneralSettingsState(
    val parseCacheSizeBytes: Long = 0L,

    /**
     * Where the last "Copy database" landed, or why it did not. Both null until
     * the row is tapped; a copy that reported nothing would leave the next
     * question to be answered from a stale file.
     */
    val databaseCopyPath: String? = null,
    val databaseCopyError: String? = null
)
