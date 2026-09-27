/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The swap at startup, on real SQLite: what it replaces, the paths it rebases
 * and the ones it must leave alone, and that it can be run again from where a
 * killed start left it.
 */
@RunWith(RobolectricTestRunner::class)
class RestoreSwapTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val oldFilesDir = "/data/user/0/ua.acclorite.book_story.release.debug/files"

    private lateinit var filesDir: File
    private lateinit var targets: RestoreTargets
    private lateinit var staging: File
    private lateinit var marker: File
    private lateinit var note: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        targets = RestoreTargets(
            filesDir = filesDir,
            database = File(temp.root, "databases/book_db"),
            dataStore = File(filesDir, "datastore/data_store.preferences_pb")
        )
        staging = File(filesDir, RestoreFiles.STAGING_DIR)
        marker = File(filesDir, RestoreFiles.PENDING_MARKER)
        note = File(filesDir, RestoreFiles.DONE_NOTE)
    }

    private val newFilesDir: String get() = filesDir.absolutePath

    private fun createDatabase(file: File, vararg rows: Pair<String?, String>) {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE BookEntity (id INTEGER PRIMARY KEY, image TEXT, filePath TEXT)")
            rows.forEach { (image, filePath) ->
                db.execSQL("INSERT INTO BookEntity (image, filePath) VALUES (?, ?)", arrayOf(image, filePath))
            }
        }
    }

    private fun readRows(file: File): List<Pair<String?, String>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT image, filePath FROM BookEntity ORDER BY id", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((if (cursor.isNull(0)) null else cursor.getString(0)) to cursor.getString(1))
                    }
                }
            }
        }

    private fun manifest(sources: List<BackupManifest.Source> = emptyList()) = BackupManifest(
        format = BACKUP_FORMAT,
        schema = 24,
        versionCode = 14,
        versionName = "1.8.0",
        applicationId = "ua.acclorite.book_story.release.debug",
        filesDir = oldFilesDir,
        createdAt = 1L,
        counts = BackupManifest.Counts(books = 2, sessions = 5, covers = 1, ownedBooks = 1),
        sources = sources
    )

    /** A staged, confirmed backup: one cover, one owned book, settings. */
    private fun stage(
        withSettings: Boolean = true,
        withCovers: Boolean = true,
        markerText: String = "0",
        sources: List<BackupManifest.Source> = emptyList()
    ) {
        File(staging, BackupEntries.MANIFEST).apply {
            parentFile?.mkdirs()
            writeText(manifest(sources).toJson())
        }
        createDatabase(
            File(staging, BackupEntries.DATABASE),
            "file://$oldFilesDir/covers/a.webp" to "/storage/emulated/0/Books/x.epub",
            null to "$oldFilesDir/owned_books/7/y.fb2"
        )
        if (withSettings) {
            File(staging, BackupEntries.SETTINGS).apply {
                parentFile?.mkdirs()
                writeText("backup settings")
            }
        }
        if (withCovers) {
            File(staging, "covers/a.webp").apply {
                parentFile?.mkdirs()
                writeText("backup cover")
            }
        }
        File(staging, "owned_books/7/y.fb2").apply {
            parentFile?.mkdirs()
            writeText("backup book")
            setLastModified(1_600_000_000_000L)
        }
        marker.writeText(markerText)
    }

    /** The library the restore replaces. */
    private fun liveLibrary() {
        createDatabase(targets.database, "file://$newFilesDir/covers/old.webp" to "/old.epub")
        File(targets.database.path + "-wal").writeText("stale wal")
        targets.dataStore.apply {
            parentFile?.mkdirs()
            writeText("live settings")
        }
        File(filesDir, "covers/old.webp").apply {
            parentFile?.mkdirs()
            writeText("live cover")
        }
        File(filesDir, "owned_books/3/old.epub").apply {
            parentFile?.mkdirs()
            writeText("live book")
        }
    }

    private fun assertRestored() {
        assertEquals(
            listOf(
                "file://$newFilesDir/covers/a.webp" to "/storage/emulated/0/Books/x.epub",
                null to "$newFilesDir/owned_books/7/y.fb2"
            ),
            readRows(targets.database)
        )
        assertFalse(File(targets.database.path + "-wal").exists())
        assertEquals("backup settings", targets.dataStore.readText())
        assertEquals(listOf("a.webp"), File(filesDir, "covers").list()!!.toList())
        assertFalse(File(filesDir, "owned_books/3").exists())
        val owned = File(filesDir, "owned_books/7/y.fb2")
        assertEquals("backup book", owned.readText())
        assertEquals(1_600_000_000_000L, owned.lastModified())

        assertFalse(staging.exists())
        assertFalse(marker.exists())
        assertFalse(RestoreOutcome.fromJson(note.readText()).failed)
    }

    @Test
    fun `nothing pending leaves everything alone`() {
        liveLibrary()
        swapPendingRestore(targets)
        assertEquals(listOf("file://$newFilesDir/covers/old.webp" to "/old.epub"), readRows(targets.database))
        assertFalse(note.exists())
    }

    @Test
    fun `a staging never confirmed is dropped and the library kept`() {
        liveLibrary()
        stage()
        marker.delete()

        swapPendingRestore(targets)

        assertFalse(staging.exists())
        assertEquals("live settings", targets.dataStore.readText())
        assertFalse(note.exists())
    }

    @Test
    fun `a marker without staging is the last step of a finished swap`() {
        marker.writeText("1")
        swapPendingRestore(targets)
        assertFalse(marker.exists())
        assertFalse(note.exists())
    }

    @Test
    fun `a pending restore replaces every part and rebases the paths`() {
        liveLibrary()
        val sources = listOf(BackupManifest.Source("Drive", "Touhou", "com.google.android.apps.docs.storage"))
        stage(sources = sources)

        swapPendingRestore(targets)

        assertRestored()
        val outcome = RestoreOutcome.fromJson(note.readText())
        assertEquals(2, outcome.books)
        assertEquals(5, outcome.sessions)
        assertEquals(sources, outcome.sources)
    }

    @Test
    fun `works on a fresh install with no library at all`() {
        stage()
        swapPendingRestore(targets)
        assertRestored()
    }

    @Test
    fun `a part the backup lacks is removed from the library`() {
        liveLibrary()
        stage(withSettings = false, withCovers = false)

        swapPendingRestore(targets)

        assertFalse(targets.dataStore.exists())
        assertFalse(File(filesDir, "covers").exists())
    }

    @Test
    fun `a swap killed halfway is finished by the next start`() {
        liveLibrary()
        stage()
        // What a start killed after the rebase and the database copy leaves:
        // the staged database already rebased, the live one already replaced,
        // the rest still old, the marker counting one attempt.
        rebasePaths(File(staging, BackupEntries.DATABASE), oldFilesDir, newFilesDir)
        File(staging, BackupEntries.DATABASE).copyTo(targets.database, overwrite = true)
        marker.writeText("1")

        swapPendingRestore(targets)

        assertRestored()
    }

    @Test
    fun `a swap that failed twice is given up and the library kept as it is`() {
        liveLibrary()
        stage(markerText = "2")

        swapPendingRestore(targets)

        assertFalse(staging.exists())
        assertFalse(marker.exists())
        assertTrue(RestoreOutcome.fromJson(note.readText()).failed)
        assertEquals("live settings", targets.dataStore.readText())
    }

    @Test
    fun `a swap that throws is tried again and then given up, never crashes`() {
        liveLibrary()
        stage()
        File(staging, BackupEntries.DATABASE).writeText("not a database")

        swapPendingRestore(targets)

        assertFalse(staging.exists())
        assertFalse(marker.exists())
        val outcome = RestoreOutcome.fromJson(note.readText())
        assertTrue(outcome.failed)
        assertEquals(2, outcome.books)
    }

    @Test
    fun `rebase leaves every path that does not start with the prefix alone`() {
        val database = File(temp.root, "rebase/book_db")
        val untouched = listOf(
            "content://com.android.providers/document/1" to "/storage/Touhou/a.fb2",
            // Would match LIKE, where `_` is a wildcard.
            "file:///data/user/0/uaXacclorite.book_story.release.debug/files/covers/b.webp" to
                    "/data/user/0/ua.acclorite.book_storyXrelease.debug/files/owned_books/1/b.epub",
            // Contains the prefix without starting with it: replace() would take it.
            "file:///sdcard/file://$oldFilesDir/covers/c.webp" to "/x$oldFilesDir/owned_books/2/c.epub",
            // The private directory, but not a cover or an owned book.
            "file://$oldFilesDir/other/d.webp" to "$oldFilesDir/books/d.epub",
            // Case counts.
            "FILE://$oldFilesDir/covers/e.webp" to "/DATA/user/0/ua.acclorite.book_story.release.debug/files/owned_books/5/e.epub"
        )
        createDatabase(database, *untouched.toTypedArray())

        rebasePaths(database, oldFilesDir, newFilesDir)

        assertEquals(untouched, readRows(database))
    }

    @Test
    fun `rebase onto the same directory changes nothing`() {
        val database = File(temp.root, "same/book_db")
        val rows = listOf("file://$oldFilesDir/covers/a.webp" to "$oldFilesDir/owned_books/1/a.epub")
        createDatabase(database, *rows.toTypedArray())

        rebasePaths(database, oldFilesDir, oldFilesDir)

        assertEquals(rows, readRows(database))
    }
}
