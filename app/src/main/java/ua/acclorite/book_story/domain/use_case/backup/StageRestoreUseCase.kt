/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.backup.BackupManifest
import ua.acclorite.book_story.data.backup.RestoreStaging
import javax.inject.Inject

private const val TAG = "StageRestore"

class StageRestoreUseCase @Inject constructor(
    private val application: Application,
    private val restoreStaging: RestoreStaging
) {

    /**
     * Unpacks and checks the backup in [uri], the document the user picked in
     * the system picker, and returns its manifest for the confirmation.
     *
     * Nothing the library uses is touched here: a backup that fails a check
     * leaves no trace, and one that passes waits in the staging until the user
     * confirms or cancels.
     */
    suspend operator fun invoke(uri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val input = application.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("The chosen file could not be opened.")
            input.use { restoreStaging.stage(it) }
        }.onFailure {
            logW(TAG, "Could not stage the backup: ${it.messageForLog()}")
        }
    }
}
