/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
sealed class BackupSettingsEvent {
    /** The user created [uri] in the system picker; write the backup there. */
    data class OnExport(val uri: Uri) : BackupSettingsEvent()

    /** The user picked [uri] to restore from; unpack and check it. */
    data class OnPickRestore(val uri: Uri) : BackupSettingsEvent()

    /**
     * From the confirmation: the user created [uri] to save the current
     * library to before it is replaced.
     */
    data class OnSaveBeforeRestore(val uri: Uri) : BackupSettingsEvent()

    /** The confirmation was dismissed; drop the staged backup. */
    data object OnCancelRestore : BackupSettingsEvent()

    /** The confirmation was accepted; hand the backup over and restart. */
    data object OnConfirmRestore : BackupSettingsEvent()
}
