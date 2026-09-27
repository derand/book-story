/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.acclorite.book_story.core.crash.CrashHandler
import ua.acclorite.book_story.data.backup.swapPendingRestore
import ua.acclorite.book_story.data.cache.ReaderImageFiles
import ua.acclorite.book_story.data.model.file.CachedFile
import javax.inject.Inject

@HiltAndroidApp
class Application : Application() {

    @Inject
    lateinit var readerImageFiles: ReaderImageFiles

    override fun onCreate() {
        // Before super.onCreate(): that is where Hilt injects, and the first
        // injection to reach the database or the settings opens their files —
        // which a pending restore is about to replace.
        swapPendingRestore(this)

        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Reader image files and book copies of runs that never got to clean up
        // after themselves — being killed, or swiped away from the recents list, is
        // an ordinary way to leave a book, so app start is the only reliable place
        // for this.
        CoroutineScope(Dispatchers.IO).launch {
            readerImageFiles.sweep()
            CachedFile.clearCopies(this@Application)
        }
    }
}
