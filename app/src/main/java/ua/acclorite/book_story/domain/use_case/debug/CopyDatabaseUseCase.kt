/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.debug

import ua.acclorite.book_story.BuildConfig
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.data.debug.DatabaseCopier
import javax.inject.Inject

private const val TAG = "CopyDatabase"

class CopyDatabaseUseCase @Inject constructor(
    private val databaseCopier: DatabaseCopier
) {

    /**
     * Where the copy landed, or the failure. A copy that quietly did nothing
     * would be worse than no button at all — the next question would be
     * answered from a stale file.
     *
     * The build-config guard is repeated here rather than left to the settings
     * row that calls it, so the flag is the one place this feature exists or
     * does not.
     */
    suspend operator fun invoke(): Result<String> {
        if (!BuildConfig.DB_EXPORT) {
            return Result.failure(IllegalStateException("Database export is off in this build."))
        }

        return runCatchingCancellable {
            databaseCopier.copy().path
        }.onFailure {
            logW(TAG, "Could not copy the database: ${it.message}")
        }
    }
}
