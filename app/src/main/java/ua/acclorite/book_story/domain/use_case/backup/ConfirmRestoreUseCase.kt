/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.backup

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.backup.RestoreStaging
import javax.inject.Inject
import kotlin.system.exitProcess

private const val TAG = "ConfirmRestore"

class ConfirmRestoreUseCase @Inject constructor(
    private val application: Application,
    private val restoreStaging: RestoreStaging
) {

    /**
     * Marks the staged backup for the next start and restarts the app. Does
     * not return.
     *
     * The swap itself cannot happen here: Room holds the database open, and
     * replacing the file under a live connection is how a library gets
     * corrupted. So the process ends, and the next one swaps the files in
     * before anything opens them.
     */
    suspend operator fun invoke(): Nothing {
        withContext(Dispatchers.IO) { restoreStaging.markPending() }
        logI(TAG, "Restore confirmed, restarting.")

        val launch = application.packageManager.getLaunchIntentForPackage(application.packageName)
            ?.component
            ?: throw IllegalStateException("The app has no launcher activity.")
        application.startActivity(Intent.makeRestartActivityTask(launch))
        exitProcess(0)
    }
}
