/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.backup.RestoreStaging
import javax.inject.Inject

class DiscardRestoreUseCase @Inject constructor(
    private val restoreStaging: RestoreStaging
) {

    /** Drops a staged backup the user decided not to restore. */
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        restoreStaging.discard()
    }
}
