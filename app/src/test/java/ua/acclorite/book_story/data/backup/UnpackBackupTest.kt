/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unpacking is the step that runs on a file from anywhere before the user has
 * confirmed anything, so what matters is what it refuses: every refusal must
 * come before a byte lands outside the staging.
 */
class UnpackBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val manifest = BackupManifest(
        format = BACKUP_FORMAT,
        schema = 24,
        versionCode = 42,
        versionName = "1.8.0",
        applicationId = "ua.acclorite.book_story.release.debug",
        filesDir = "/data/user/0/ua.acclorite.book_story.release.debug/files",
        createdAt = 1_790_000_000_000,
        counts = BackupManifest.Counts(books = 2, sessions = 3, covers = 1, ownedBooks = 1),
        sources = emptyList()
    )

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun backup(vararg extra: Pair<String, String>) = zip(
        BackupEntries.MANIFEST to manifest.toJson(),
        BackupEntries.DATABASE to "db",
        BackupEntries.SETTINGS to "prefs",
        BackupEntries.COVERS + "1.webp" to "cover",
        BackupEntries.OWNED_BOOKS + "7/book.epub" to "book",
        *extra
    )

    private fun refusal(input: ByteArrayInputStream, maxSchema: Int = 24): RestoreException {
        val staging = File(temp.root, "staging")
        return assertThrows(RestoreException::class.java) {
            unpackBackup(input, staging, maxSchema)
        }
    }

    @Test
    fun `a backup unpacks as laid out in the zip`() {
        val staging = File(temp.root, "staging")
        val read = unpackBackup(backup(), staging, maxSchema = 24)

        assertEquals(manifest, read)
        assertEquals("db", File(staging, BackupEntries.DATABASE).readText())
        assertEquals("prefs", File(staging, BackupEntries.SETTINGS).readText())
        assertEquals("cover", File(staging, "covers/1.webp").readText())
        assertEquals("book", File(staging, "owned_books/7/book.epub").readText())
    }

    @Test
    fun `an older schema is taken, Room migrates it`() {
        unpackBackup(backup(), File(temp.root, "staging"), maxSchema = 30)
    }

    @Test
    fun `a newer schema is refused as too new`() {
        assertEquals(RestoreException.Reason.TOO_NEW, refusal(backup(), maxSchema = 23).reason)
    }

    @Test
    fun `a newer archive format is refused as too new`() {
        val input = zip(
            BackupEntries.MANIFEST to manifest.copy(format = BACKUP_FORMAT + 1).toJson(),
            BackupEntries.DATABASE to "db"
        )
        assertEquals(RestoreException.Reason.TOO_NEW, refusal(input).reason)
    }

    @Test
    fun `a zip without a manifest is not a backup`() {
        val input = zip(BackupEntries.DATABASE to "db")
        assertEquals(RestoreException.Reason.NOT_A_BACKUP, refusal(input).reason)
    }

    @Test
    fun `a manifest that does not read is not a backup`() {
        val input = zip(BackupEntries.MANIFEST to "{}", BackupEntries.DATABASE to "db")
        assertEquals(RestoreException.Reason.NOT_A_BACKUP, refusal(input).reason)
    }

    @Test
    fun `a file that is not a zip is not a backup`() {
        val input = ByteArrayInputStream("plain text".toByteArray())
        assertEquals(RestoreException.Reason.NOT_A_BACKUP, refusal(input).reason)
    }

    @Test
    fun `a backup without a database is damaged`() {
        val input = zip(BackupEntries.MANIFEST to manifest.toJson())
        assertEquals(RestoreException.Reason.DAMAGED, refusal(input).reason)
    }

    @Test
    fun `an entry climbing out of the staging is refused and writes nothing`() {
        val input = backup(BackupEntries.OWNED_BOOKS + "../../evil.txt" to "x")
        assertEquals(RestoreException.Reason.DAMAGED, refusal(input).reason)
        assertFalse(File(temp.root, "evil.txt").exists())
    }

    @Test
    fun `a cover in a subdirectory is refused`() {
        val input = backup(BackupEntries.COVERS + "../databases/book_db" to "x")
        assertEquals(RestoreException.Reason.DAMAGED, refusal(input).reason)
        assertFalse(File(temp.root, "databases/book_db").exists())
    }

    @Test
    fun `an entry that is not part of a backup is refused`() {
        val input = backup("shared_prefs/x.xml" to "x")
        assertEquals(RestoreException.Reason.DAMAGED, refusal(input).reason)
    }

    @Test
    fun `an entry that appears twice is refused`() {
        // ZipOutputStream will not write a name twice, so the second one is
        // written under a name of the same length and renamed in the bytes.
        val decoy = BackupEntries.DATABASE.dropLast(1) + "X"
        val bytes = zip(
            BackupEntries.MANIFEST to manifest.toJson(),
            BackupEntries.DATABASE to "db",
            decoy to "another"
        ).readBytes()
        val patched = String(bytes, Charsets.ISO_8859_1)
            .replace(decoy, BackupEntries.DATABASE)
            .toByteArray(Charsets.ISO_8859_1)
        val input = ByteArrayInputStream(patched)
        assertEquals(RestoreException.Reason.DAMAGED, refusal(input).reason)
    }

    @Test
    fun `directory entries a repacked zip may carry are skipped`() {
        val staging = File(temp.root, "staging")
        unpackBackup(backup("owned_books/8/" to "", "covers/" to ""), staging, maxSchema = 24)
        assertTrue(File(staging, BackupEntries.DATABASE).isFile)
    }
}
