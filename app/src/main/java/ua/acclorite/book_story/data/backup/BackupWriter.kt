/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.preferencesDataStoreFile
import ua.acclorite.book_story.BuildConfig
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.local.room.DatabaseSnapshot
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupWriter"

/** The name `DataStoreImpl` gives `preferencesDataStore`. */
private const val DATA_STORE_NAME = "data_store"

/**
 * Writes everything an uninstall would take into one zip: the database, the
 * settings, the covers and the books the app keeps a copy of.
 *
 * **The copied books are not optional.** They are books handed over by an app
 * that exposes no location — the Downloads provider among them — so the copy
 * is the only one there is, and no folder can be granted again to find it.
 *
 * **Settings are copied as the DataStore file, whole**, never key by key. A
 * list of keys is one more place every new setting has to be added to, and the
 * one that gets forgotten breaks restore for everybody after it.
 *
 * Left out, on purpose: the parse cache, which rebuilds itself — the first
 * open of each book after a restore is slow again — and the crash reports.
 */
@Singleton
class BackupWriter @Inject constructor(
    private val application: Application,
    private val snapshot: DatabaseSnapshot
) {

    private val filesDir: File get() = application.filesDir

    /**
     * Writes the archive to [output] and returns what went into it, with the
     * number of bytes written. [sources] are the granted folders, recorded so
     * the restore can say which ones to grant again.
     *
     * Throws on any failure; what was written by then is not a backup, and
     * the caller is the one that can delete it.
     */
    fun write(output: OutputStream, sources: List<BackupManifest.Source>): BackupResult {
        val workDir = File(application.cacheDir, "backup").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            // The database is copied out first and read from the copy, so the
            // counts in the manifest describe exactly the file in the archive.
            val database = snapshot.copyTo(File(workDir, "book_db"))
            val (schema, books, sessions) = readDatabase(database)

            val covers = File(filesDir, "covers").listFiles()
                ?.filter { it.isFile }
                .orEmpty()
            val ownedRoot = File(filesDir, "owned_books")
            val ownedBooks = ownedRoot.walkTopDown().filter { it.isFile }.toList()
            val settings = application.preferencesDataStoreFile(DATA_STORE_NAME)

            val manifest = BackupManifest(
                format = BACKUP_FORMAT,
                schema = schema,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                applicationId = BuildConfig.APPLICATION_ID,
                filesDir = filesDir.absolutePath,
                createdAt = System.currentTimeMillis(),
                counts = BackupManifest.Counts(
                    books = books,
                    sessions = sessions,
                    covers = covers.size,
                    ownedBooks = ownedBooks.size
                ),
                sources = sources
            )

            val counting = CountingOutputStream(output)
            ZipOutputStream(counting.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BackupEntries.MANIFEST))
                zip.write(manifest.toJson().toByteArray())
                zip.closeEntry()

                zip.addFile(BackupEntries.DATABASE, database)

                // Absent until the first setting is changed; a restore without
                // it keeps the defaults, which is what the backup had too.
                if (settings.isFile) zip.addFile(BackupEntries.SETTINGS, settings)

                covers.forEach { zip.addFile(BackupEntries.COVERS + it.name, it) }
                ownedBooks.forEach { file ->
                    val relative = file.relativeTo(ownedRoot).invariantSeparatorsPath
                    zip.addFile(BackupEntries.OWNED_BOOKS + relative, file)
                }
            }

            logI(
                TAG,
                "Wrote ${counting.count} bytes: $books books, $sessions sessions, " +
                        "${covers.size} covers, ${ownedBooks.size} owned books."
            )
            return BackupResult(manifest = manifest, bytes = counting.count)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** The schema version, the library's size and the number of sessions. */
    private fun readDatabase(file: File): Triple<Int, Int, Int> {
        // Read-write on a private copy: a read-only open of a database in WAL
        // mode fails when it cannot create the -shm file beside it.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun count(sql: String): Int = db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            return Triple(
                db.version,
                count("SELECT COUNT(*) FROM BookEntity WHERE inLibrary = 1"),
                count("SELECT COUNT(*) FROM ReadingSessionEntity")
            )
        }
    }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        // The modification time travels with the file: a book the app keeps a
        // copy of carries its source's, and the parse cache keys on it.
        putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}

/** What a finished backup holds, and how large it came out. */
data class BackupResult(
    val manifest: BackupManifest,
    val bytes: Long
)

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
