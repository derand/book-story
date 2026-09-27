/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.backup.BackupManifest
import ua.acclorite.book_story.data.backup.BackupResult
import ua.acclorite.book_story.data.backup.BackupWriter
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import javax.inject.Inject

private const val TAG = "ExportBackup"

class ExportBackupUseCase @Inject constructor(
    private val application: Application,
    private val backupWriter: BackupWriter,
    private val getBookSourcesUseCase: GetBookSourcesUseCase
) {

    /**
     * Writes a backup into [uri], the document the user just created in the
     * system picker, and returns what went into it.
     *
     * The picker's grant is a one-shot one and covers exactly this document —
     * no storage permission is involved, and nothing is kept afterwards.
     *
     * On failure the half-written document is deleted: left in place it looks
     * like a backup, and it would be found at the one moment it is needed.
     */
    suspend operator fun invoke(uri: Uri): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val sources = getBookSourcesUseCase().map { source ->
                BackupManifest.Source(
                    provider = source.provider,
                    name = source.name,
                    authority = source.authority
                )
            }

            // "wt": some providers open plain "w" without truncating, and a
            // shorter archive written over a longer one would keep its tail.
            val output = application.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("The chosen file could not be opened.")
            output.use { backupWriter.write(it, sources) }
        }.onFailure {
            logW(TAG, "Could not write the backup: ${it.messageForLog()}")
            runCatching { DocumentsContract.deleteDocument(application.contentResolver, uri) }
        }
    }
}
