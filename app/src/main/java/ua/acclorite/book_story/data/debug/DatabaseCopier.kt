/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.debug

import android.app.Application
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.local.room.DatabaseSnapshot
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DatabaseCopier"

/** Where the copy lands. `adb shell` can read it; `run-as` is not needed. */
private const val DIRECTORY = "debug"

/**
 * Copies the database somewhere it can actually be read.
 *
 * The app that does the reading is `release-debug`, which is deliberately not
 * `debuggable` — that flag would cost every `BookTiming` measurement its
 * comparability — so `run-as` cannot reach its files. This puts a copy under
 * the app's own external files directory instead, which adb reads directly.
 */
@Singleton
class DatabaseCopier @Inject constructor(
    private val application: Application,
    private val snapshot: DatabaseSnapshot
) {

    /**
     * Copies the database out as one consistent file — see [DatabaseSnapshot]
     * for why that takes a checkpoint — and returns where it landed.
     */
    fun copy(): File {
        val destination = File(application.getExternalFilesDir(null), DIRECTORY).apply {
            mkdirs()
        }

        val copied = snapshot.copyTo(File(destination, snapshot.file.name))

        logI(TAG, "Copied ${copied.length()} bytes to ${copied.path}.")
        return copied
    }
}
