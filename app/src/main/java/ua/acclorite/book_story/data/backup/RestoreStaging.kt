/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.local.room.DATABASE_VERSION
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RestoreStaging"

/**
 * Where a restore keeps its files between being chosen and being swapped in.
 *
 * In `filesDir` and not the cache: the system may empty the cache at any time,
 * and the app sweeps parts of it itself on every start — which is exactly when
 * the swap reads from here.
 */
object RestoreFiles {
    /** The unpacked backup, laid out as in the zip. */
    const val STAGING_DIR = "restore-staging"

    /**
     * Present only while a confirmed restore waits for the next start. Written
     * after the staging is complete and deleted after the swap is, so a swap
     * that was interrupted is simply done again.
     */
    const val PENDING_MARKER = "restore-pending"
}

/**
 * Why a backup was refused. Every one of them leaves the library as it was.
 */
class RestoreException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** No manifest, or one this app did not write. */
        NOT_A_BACKUP,

        /** The archive's layout or the database is newer than this app. */
        TOO_NEW,

        /** Unreadable, an entry that should not be there, or a bad database. */
        DAMAGED
    }
}

/**
 * The first half of a restore: unpacks a backup into [RestoreFiles.STAGING_DIR]
 * and checks it, before anything the library uses is touched.
 *
 * Every check runs here, so that the confirmation is only ever shown for a
 * backup the swap can take. A backup that fails any of them is deleted from the
 * staging, and the library is left as it was.
 */
@Singleton
class RestoreStaging @Inject constructor(
    private val application: Application
) {

    private val stagingDir: File get() = File(application.filesDir, RestoreFiles.STAGING_DIR)
    private val marker: File get() = File(application.filesDir, RestoreFiles.PENDING_MARKER)

    /**
     * Unpacks [input] and checks it; returns its manifest or throws, with
     * nothing left behind in the second case.
     */
    fun stage(input: InputStream): BackupManifest {
        discard()
        try {
            val manifest = unpackBackup(input, stagingDir)
            checkDatabase(File(stagingDir, BackupEntries.DATABASE), manifest)

            logI(
                TAG,
                "Staged a backup: schema ${manifest.schema}, ${manifest.counts.books} books, " +
                        "${manifest.counts.sessions} sessions."
            )
            return manifest
        } catch (e: Exception) {
            discard()
            throw e
        }
    }

    /** Drops a staged backup that was not confirmed, and any pending restore. */
    fun discard() {
        marker.delete()
        stagingDir.deleteRecursively()
    }

    /** Hands the staged backup to the next start, which swaps it in. */
    fun markPending() {
        check(File(stagingDir, BackupEntries.MANIFEST).isFile) { "Nothing is staged." }
        marker.writeText(System.currentTimeMillis().toString())
    }

    /**
     * Opens the staged database and asks SQLite whether it is whole, and
     * whether it is the version the manifest promised.
     */
    private fun checkDatabase(file: File, manifest: BackupManifest) {
        // Read-write on a private copy: a read-only open of a database in WAL
        // mode fails when it cannot create the -shm file beside it.
        val version = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (integrity != "ok") {
                    throw RestoreException(
                        RestoreException.Reason.DAMAGED,
                        "The database fails its integrity check."
                    )
                }
                db.version
            }
        } catch (e: RestoreException) {
            throw e
        } catch (e: Exception) {
            throw RestoreException(
                RestoreException.Reason.DAMAGED,
                "The database does not open: ${e.javaClass.simpleName}."
            )
        }

        // The file decides, not the manifest: a mismatch means one of them was
        // not written by the backup the other came from.
        if (version != manifest.schema) {
            throw RestoreException(
                RestoreException.Reason.DAMAGED,
                "The database is version $version, the manifest says ${manifest.schema}."
            )
        }
    }
}

/**
 * Unpacks a backup into [directory], which must not exist yet or be empty, and
 * returns its manifest once the manifest has been checked by [checkManifest].
 *
 * Only the entries a backup has are taken, each once, and each is checked to
 * land inside [directory]: the zip is a file from anywhere, and a name such as
 * `../databases/book_db` would otherwise write straight over the live library
 * before any confirmation was asked for.
 */
internal fun unpackBackup(
    input: InputStream,
    directory: File,
    maxSchema: Int = DATABASE_VERSION
): BackupManifest {
    directory.mkdirs()
    val root = directory.canonicalFile
    val seen = HashSet<String>()
    var manifest: BackupManifest? = null

    try {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name

                // Directory entries are not written by the backup, but a zip
                // repacked by hand may have them; they carry nothing.
                if (entry.isDirectory) {
                    if (!isAllowedDirectory(name)) throw damaged("An entry that is not part of a backup.")
                    continue
                }
                if (!isAllowedEntry(name)) throw damaged("An entry that is not part of a backup.")
                if (!seen.add(name)) throw damaged("An entry that appears twice.")

                val destination = File(root, name)
                val canonical = runCatching { destination.canonicalFile }.getOrNull()
                if (canonical == null || !canonical.path.startsWith(root.path + File.separator)) {
                    throw damaged("An entry that points outside the backup.")
                }

                if (name == BackupEntries.MANIFEST) {
                    // Checked as soon as it is read, so a backup from a newer app
                    // is refused before the rest of it is unpacked.
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    manifest = checkManifest(text, maxSchema)
                    canonical.writeText(text)
                    continue
                }

                canonical.parentFile?.mkdirs()
                canonical.outputStream().use { zip.copyTo(it) }
                if (entry.time != -1L) canonical.setLastModified(entry.time)
            }
        }
    } catch (e: ZipException) {
        throw damaged("The file is not a readable zip.")
    }

    val checked = manifest ?: throw RestoreException(
        RestoreException.Reason.NOT_A_BACKUP,
        "There is no manifest."
    )
    if (BackupEntries.DATABASE !in seen) throw damaged("There is no database.")
    return checked
}

/**
 * Reads a manifest and refuses one this app cannot take: a layout it does not
 * know, or a database newer than [maxSchema]. An older database is fine — Room
 * migrates it on the next start.
 */
internal fun checkManifest(text: String, maxSchema: Int): BackupManifest {
    val manifest = try {
        BackupManifest.fromJson(text)
    } catch (e: Exception) {
        throw RestoreException(
            RestoreException.Reason.NOT_A_BACKUP,
            "The manifest does not read: ${e.message}"
        )
    }

    if (manifest.format > BACKUP_FORMAT) {
        throw RestoreException(
            RestoreException.Reason.TOO_NEW,
            "Archive format ${manifest.format}, this app reads $BACKUP_FORMAT."
        )
    }
    if (manifest.format != BACKUP_FORMAT) {
        throw RestoreException(
            RestoreException.Reason.NOT_A_BACKUP,
            "Unknown archive format ${manifest.format}."
        )
    }
    if (manifest.schema > maxSchema) {
        throw RestoreException(
            RestoreException.Reason.TOO_NEW,
            "Database version ${manifest.schema}, this app reads up to $maxSchema."
        )
    }
    if (manifest.schema < 1) {
        throw RestoreException(
            RestoreException.Reason.NOT_A_BACKUP,
            "Database version ${manifest.schema}."
        )
    }
    return manifest
}

private fun isAllowedEntry(name: String): Boolean = when {
    name == BackupEntries.MANIFEST -> true
    name == BackupEntries.DATABASE -> true
    name == BackupEntries.SETTINGS -> true
    // Covers are flat, one file per book.
    name.startsWith(BackupEntries.COVERS) ->
        name.length > BackupEntries.COVERS.length && '/' !in name.removePrefix(BackupEntries.COVERS)
    // A directory per book, the file inside it.
    name.startsWith(BackupEntries.OWNED_BOOKS) ->
        name.length > BackupEntries.OWNED_BOOKS.length
    else -> false
}

private fun isAllowedDirectory(name: String): Boolean =
    name == BackupEntries.COVERS ||
            name.startsWith(BackupEntries.OWNED_BOOKS) ||
            name == BackupEntries.DATABASE.substringBeforeLast('/') + "/" ||
            name == BackupEntries.SETTINGS.substringBeforeLast('/') + "/"

private fun damaged(message: String) = RestoreException(RestoreException.Reason.DAMAGED, message)
