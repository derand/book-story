/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.backup.BackupManifest
import ua.acclorite.book_story.data.backup.RestoreOutcomeNote
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import javax.inject.Inject

/** What the Library tells the user on the first start after a restore. */
@Immutable
data class RestoreNotice(
    val failed: Boolean,
    val books: Int,
    val sessions: Int,
    /** The backup's folders that are not granted now, in the backup's order. */
    val missingSources: List<BackupManifest.Source>
)

class GetRestoreNoticeUseCase @Inject constructor(
    private val restoreOutcomeNote: RestoreOutcomeNote,
    private val getBookSourcesUseCase: GetBookSourcesUseCase
) {

    /**
     * The notice for the last restore, or null when there is nothing to tell.
     *
     * A folder counts as granted when a grant has the same provider authority
     * and the same folder name. Not the tree's URI: on Drive the document id
     * differs across installs for the very same folder. Two folders of one name
     * on one provider are therefore one — a rare case, and the cost is a hint
     * that says too little, never one that asks for too much.
     */
    suspend operator fun invoke(): RestoreNotice? = withContext(Dispatchers.IO) {
        val outcome = restoreOutcomeNote.read() ?: return@withContext null

        val granted = getBookSourcesUseCase().map { it.authority to it.name }.toSet()
        RestoreNotice(
            failed = outcome.failed,
            books = outcome.books,
            sessions = outcome.sessions,
            missingSources = outcome.sources
                .filter { (it.authority to it.name) !in granted }
                .distinct()
        )
    }
}
