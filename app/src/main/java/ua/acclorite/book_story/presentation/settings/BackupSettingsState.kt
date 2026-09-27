/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.data.backup.BackupManifest
import ua.acclorite.book_story.data.backup.BackupResult
import ua.acclorite.book_story.data.backup.RestoreException

@Immutable
data class BackupSettingsState(
    /** A backup is being written; the row takes no taps until it is done. */
    val exporting: Boolean = false,
    /** The last backup written in this session, or null. */
    val lastExport: BackupResult? = null,
    /** Why the last backup failed, or null. */
    val exportError: String? = null,

    /** A picked backup is being unpacked and checked. */
    val staging: Boolean = false,
    /**
     * A backup that passed every check and waits for the confirmation; the
     * dialog is shown while this is not null.
     */
    val stagedRestore: BackupManifest? = null,
    /** Why the picked backup was refused, or null. */
    val restoreError: RestoreFailure? = null,
    /** The current library, saved from the confirmation, or null. */
    val savedBeforeRestore: BackupResult? = null,
    /** Confirmed: the app is about to restart. */
    val restarting: Boolean = false
)

@Immutable
data class RestoreFailure(
    /** Null when the failure was not one of the checks — an unreadable file. */
    val reason: RestoreException.Reason?,
    val detail: String
)
