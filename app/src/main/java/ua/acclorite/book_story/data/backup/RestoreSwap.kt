/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.preferencesDataStoreFile
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import java.io.File

private const val TAG = "RestoreSwap"

/**
 * How many starts may try a swap before it is given up. A start that was
 * killed halfway counts as one: nothing tells it apart from one that crashed.
 */
internal const val MAX_SWAP_ATTEMPTS = 2

/** SQLite's companions of a database file, stale the moment the file is replaced. */
private val DATABASE_SIDECARS = listOf("-wal", "-shm", "-journal")

/** Where the swap puts each part of the library. */
internal class RestoreTargets(
    val filesDir: File,
    val database: File,
    val dataStore: File
)

/**
 * The second half of a restore: swaps a confirmed, staged backup in place of
 * the live library. Does nothing when no restore is pending.
 *
 * **Must run before anything opens the database or the settings** — in
 * `Application.onCreate()` ahead of `super.onCreate()`, where Hilt injects and
 * the first injection that reaches Room or DataStore opens their files. Nothing
 * here may go through Hilt for the same reason.
 *
 * **Safe to run again from any point.** The process can die anywhere in here,
 * so no step depends on the one before it having finished: the paths are
 * rebased in the *staged* database, which a second run finds already rebased;
 * each part is deleted and then *copied*, never moved, so the staging stays
 * whole until the end; and the marker goes last. Copying costs the space of the
 * library twice over for a moment, which is tens of megabytes.
 *
 * A swap that fails is tried again, [MAX_SWAP_ATTEMPTS] times in all, and then
 * given up: the staging goes, the app starts with whatever is in place, and
 * the Library says the restore failed. Crashing on every start instead would
 * leave no way into the app at all, so nothing in here throws — not even when
 * the marker or the note cannot be written.
 */
fun swapPendingRestore(context: Context) {
    swapPendingRestore(
        RestoreTargets(
            filesDir = context.filesDir,
            database = context.getDatabasePath(LiveFiles.DATABASE),
            dataStore = context.preferencesDataStoreFile(LiveFiles.DATA_STORE)
        )
    )
}

internal fun swapPendingRestore(targets: RestoreTargets) {
    try {
        runSwap(targets)
    } catch (e: Exception) {
        // A step outside the attempts failed: the marker or the note would not
        // write, most likely on a disk the swap's second copy filled. Thrown
        // from here it would crash this start and, with the staging still
        // holding the space, every start after it.
        logE(TAG, "The restore could not finish: ${e.messageForLog()}")
        abandon(targets)
    }
}

private fun runSwap(targets: RestoreTargets) {
    val staging = File(targets.filesDir, RestoreFiles.STAGING_DIR)
    val marker = File(targets.filesDir, RestoreFiles.PENDING_MARKER)
    val manifestFile = File(staging, BackupEntries.MANIFEST)

    if (!marker.exists()) {
        // Staged but never confirmed: the process died with the dialog open,
        // which discards nothing by itself.
        if (staging.exists()) {
            staging.deleteRecursively()
            logI(TAG, "Dropped a staged backup that was never confirmed.")
        }
        return
    }
    if (!manifestFile.isFile) {
        // The staging is deleted only once the swap is complete, so all that
        // was left to do is this.
        marker.delete()
        logI(TAG, "The last swap was complete but for its marker.")
        return
    }

    val manifest = runCatching { BackupManifest.fromJson(manifestFile.readText()) }.getOrNull()
    // Unreadable is counted as one attempt made: whatever wrote it half did not
    // get far, and a marker that never parses must not be retried forever.
    var attempts = marker.readText().trim().toIntOrNull() ?: 1

    while (attempts < MAX_SWAP_ATTEMPTS) {
        attempts++
        // Before the attempt, so a start killed in the middle of it is counted.
        marker.writeText(attempts.toString())
        try {
            checkNotNull(manifest) { "The staged manifest does not read." }
            swapIn(staging, manifest, targets)
            finish(
                targets, staging, marker,
                RestoreOutcome(
                    failed = false,
                    books = manifest.counts.books,
                    sessions = manifest.counts.sessions,
                    sources = manifest.sources
                )
            )
            logI(
                TAG,
                "Restored ${manifest.counts.books} books, ${manifest.counts.sessions} sessions " +
                        "(attempt $attempts)."
            )
            return
        } catch (e: Exception) {
            logE(TAG, "Swap attempt $attempts failed: ${e.messageForLog()}")
        }
    }

    finish(
        targets, staging, marker,
        RestoreOutcome(
            failed = true,
            books = manifest?.counts?.books ?: 0,
            sessions = manifest?.counts?.sessions ?: 0,
            sources = manifest?.sources.orEmpty()
        )
    )
    logE(TAG, "Gave the restore up after $attempts attempts.")
}

private fun swapIn(staging: File, manifest: BackupManifest, targets: RestoreTargets) {
    val database = File(staging, BackupEntries.DATABASE)
    rebasePaths(database, from = manifest.filesDir, to = targets.filesDir.absolutePath)

    replaceDatabase(targets.database, database)

    // A part the backup does not have is not left over from the library it
    // replaces: no settings file means the backup had the defaults.
    targets.dataStore.delete()
    File(staging, BackupEntries.SETTINGS).takeIf { it.isFile }?.let { settings ->
        targets.dataStore.parentFile?.mkdirs()
        settings.copyTo(targets.dataStore, overwrite = true)
    }

    replaceTree(
        File(targets.filesDir, LiveFiles.COVERS),
        File(staging, BackupEntries.COVERS.trimEnd('/'))
    )
    replaceTree(
        File(targets.filesDir, LiveFiles.OWNED_BOOKS),
        File(staging, BackupEntries.OWNED_BOOKS.trimEnd('/'))
    )
}

/**
 * Moves the database's paths into this install's private directory.
 *
 * Only two columns point there: a cover's `file://` URI, and the path of a book
 * the app keeps a copy of. They carry the Android user id and the package, so
 * a backup from a work profile or from another variant of the app points at a
 * directory this one cannot read. Everything else — books in granted folders,
 * the Drive paths — is left as it is.
 *
 * Matched on the whole prefix with `substr`, not with `LIKE`, where the `_` of
 * `book_story` is a wildcard and case does not count, and not with `replace()`,
 * which would also rewrite a path that merely contains the prefix. Repeatable:
 * once rebased, the old prefix no longer matches.
 */
internal fun rebasePaths(database: File, from: String, to: String) {
    SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
        if (from != to) {
            val rebase = "SET %1\$s = ?1 || substr(%1\$s, length(?2) + 1) " +
                    "WHERE substr(%1\$s, 1, length(?2)) = ?2"
            db.execSQL(
                "UPDATE BookEntity " + rebase.format("image"),
                arrayOf("file://$to/${LiveFiles.COVERS}/", "file://$from/${LiveFiles.COVERS}/")
            )
            db.execSQL(
                "UPDATE BookEntity " + rebase.format("filePath"),
                arrayOf("$to/${LiveFiles.OWNED_BOOKS}/", "$from/${LiveFiles.OWNED_BOOKS}/")
            )
        }

        // Into the main file, so that file alone is the database: the sidecars
        // are deleted below, and so are the live ones it replaces.
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "The checkpoint was blocked." }
        }
    }
    DATABASE_SIDECARS.forEach { File(database.path + it).delete() }
}

private fun replaceDatabase(live: File, staged: File) {
    live.delete()
    DATABASE_SIDECARS.forEach { File(live.path + it).delete() }
    live.parentFile?.mkdirs()
    staged.copyTo(live, overwrite = true)
}

/**
 * Replaces [live] with a copy of [staged], or with nothing when the backup had
 * none. Modification times go across: the parse cache keys a book the app keeps
 * a copy of on it.
 */
private fun replaceTree(live: File, staged: File) {
    live.deleteRecursively()
    if (!staged.isDirectory) return
    staged.walkTopDown().filter { it.isFile }.forEach { file ->
        val target = File(live, file.relativeTo(staged).path)
        target.parentFile?.mkdirs()
        file.copyTo(target, overwrite = true)
        target.setLastModified(file.lastModified())
    }
}

/**
 * Leaves the note for the Library, then drops the staging, then the marker —
 * in that order, so a death between any two of them still ends in a note and
 * no staging.
 */
private fun finish(targets: RestoreTargets, staging: File, marker: File, outcome: RestoreOutcome) {
    writeNote(targets, outcome)
    staging.deleteRecursively()
    marker.delete()
}

/**
 * The way out when even giving up would not write: the staging goes first, to
 * free the space, and then the note is tried once more. Nothing in here throws.
 * A death before the note leaves a marker without staging, which the next
 * start takes for a finished swap — the one case where the user is not told.
 */
private fun abandon(targets: RestoreTargets) {
    File(targets.filesDir, RestoreFiles.STAGING_DIR).deleteRecursively()
    runCatching {
        writeNote(targets, RestoreOutcome(failed = true, books = 0, sessions = 0, sources = emptyList()))
    }.onFailure { logE(TAG, "The note would not write either: ${it.messageForLog()}") }
    File(targets.filesDir, RestoreFiles.PENDING_MARKER).delete()
}

private fun writeNote(targets: RestoreTargets, outcome: RestoreOutcome) {
    val note = File(targets.filesDir, RestoreFiles.DONE_NOTE)
    val partial = File(note.path + ".tmp")
    partial.writeText(outcome.toJson())
    partial.renameTo(note)
}
