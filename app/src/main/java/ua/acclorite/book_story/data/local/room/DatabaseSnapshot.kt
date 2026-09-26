/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import ua.acclorite.book_story.core.log.logI
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DatabaseSnapshot"

/** How many times a copy is retried when a write lands in the middle of it. */
private const val ATTEMPTS = 3

/**
 * A copy of the live database taken as one consistent file, while Room keeps
 * it open.
 *
 * **The checkpoint is the whole point of the ordering.** SQLite keeps recent
 * writes in `-wal`, and the main file on its own can be a 4 KB header holding
 * nothing — a copy taken without checkpointing would be missing exactly the
 * sessions worth keeping. `TRUNCATE` folds the log back in and empties it.
 *
 * What it cannot do is stop a write arriving while the file is being read: a
 * reading session saved at that moment goes to the log, and a later automatic
 * checkpoint could fold it into pages already copied. So the log is looked at
 * again after the copy. Every write goes through it, and only a `TRUNCATE`
 * checkpoint — which nothing but this class asks for — shrinks it back to
 * nothing, so a log still empty afterwards means nothing was written while the
 * copy was taken. A log that grew means the copy is thrown away and taken
 * again.
 *
 * `VACUUM INTO` would give the same guarantee in one statement, but it needs
 * SQLite 3.27, which Android ships from API 30; `minSdk` is 26.
 */
@Singleton
class DatabaseSnapshot @Inject constructor(
    private val database: BookDatabase
) {

    /** The live database file. */
    val file: File
        get() = File(database.openHelper.writableDatabase.path!!)

    /**
     * Copies the database to [destination], replacing whatever is there, and
     * returns it. Throws if no quiet moment came in [ATTEMPTS] tries.
     */
    fun copyTo(destination: File): File {
        val source = file
        val wal = File(source.path + "-wal")

        repeat(ATTEMPTS) { attempt ->
            checkpoint()
            source.copyTo(destination, overwrite = true)

            if (!wal.exists() || wal.length() == 0L) {
                logI(TAG, "Copied ${destination.length()} bytes.")
                return destination
            }
            logI(TAG, "A write landed during copy ${attempt + 1}; taking it again.")
        }

        destination.delete()
        throw IllegalStateException("The database kept changing while it was copied.")
    }

    /** Folds the write-ahead log into the main file and empties it. */
    fun checkpoint() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                // The result says whether it blocked; reading it is what runs it.
                if (cursor.moveToFirst()) {
                    logI(TAG, "Checkpointed: ${cursor.getInt(0)} busy.")
                }
            }
    }
}
