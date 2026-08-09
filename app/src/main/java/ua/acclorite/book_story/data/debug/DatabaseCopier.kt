/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.debug

import android.app.Application
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.local.room.BookDatabase
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
    private val database: BookDatabase
) {

    /**
     * Checkpoints the write-ahead log and copies the database out, returning
     * where it landed.
     *
     * **The checkpoint is the whole point of the ordering.** SQLite keeps
     * recent writes in `-wal`, and the main file on its own can be a 4 KB
     * header holding nothing — a copy taken without checkpointing would be
     * missing exactly the sessions worth asking about. `TRUNCATE` folds the
     * log back in and empties it; the `-wal`/`-shm` files are copied anyway if
     * anything is still there, so a copy is complete or it is nothing.
     */
    fun copy(): File {
        database.openHelper.writableDatabase.let { db ->
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                // The result says whether it blocked; reading it is what runs it.
                if (cursor.moveToFirst()) {
                    logI(TAG, "Checkpointed: ${cursor.getInt(0)} busy.")
                }
            }
        }

        val source = File(database.openHelper.writableDatabase.path!!)
        val destination = File(application.getExternalFilesDir(null), DIRECTORY).apply {
            mkdirs()
        }

        val copied = File(destination, source.name)
        source.copyTo(copied, overwrite = true)

        for (suffix in listOf("-wal", "-shm")) {
            val extra = File(source.path + suffix)
            if (extra.exists()) extra.copyTo(File(destination, extra.name), overwrite = true)
        }

        logI(TAG, "Copied ${copied.length()} bytes to ${copied.path}.")
        return copied
    }
}
