/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.data.backup.BackupResult

@Immutable
data class BackupSettingsState(
    /** A backup is being written; the row takes no taps until it is done. */
    val exporting: Boolean = false,
    /** The last backup written in this session, or null. */
    val lastExport: BackupResult? = null,
    /** Why the last backup failed, or null. */
    val exportError: String? = null
)
