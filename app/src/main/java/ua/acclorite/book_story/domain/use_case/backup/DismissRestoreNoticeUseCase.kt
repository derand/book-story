/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.backup.RestoreOutcomeNote
import javax.inject.Inject

class DismissRestoreNoticeUseCase @Inject constructor(
    private val restoreOutcomeNote: RestoreOutcomeNote
) {

    /**
     * Forgets the last restore's outcome, once the user has seen it. Until
     * then it is shown on every start: a process killed with the notice open
     * has not told anybody anything.
     */
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        restoreOutcomeNote.clear()
    }
}
