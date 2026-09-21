/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What a book's copy may be named, given that the name is whatever another app's
 * provider answered. The copying itself needs a provider and a real
 * `filesDir`; deciding the destination does not, and that is where the defect was.
 */
class StoredFileInTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** The book's own directory, as `store` composes it: `owned_books/<id>`. */
    private val directory: File
        get() = File(temporaryFolder.root, "owned_books/7")

    @Test
    fun `an ordinary name is stored under itself`() {
        assertEquals(File(directory, "solaris.fb2"), storedFileIn(directory, "solaris.fb2"))
    }

    @Test
    fun `names keep their spaces, dots and punctuation`() {
        assertEquals(
            File(directory, "Vol. 1 (2005) — Sci-Fi & Co..epub"),
            storedFileIn(directory, "Vol. 1 (2005) — Sci-Fi & Co..epub")
        )
    }

    /** The defect: the name decided where the file went. */
    @Test
    fun `a name climbing out of the directory keeps only its last component`() {
        assertEquals(
            File(directory, "x.epub"),
            storedFileIn(directory, "../../../x.epub")
        )
    }

    @Test
    fun `a name that is a path is taken as the file it ends on`() {
        assertEquals(File(directory, "solaris.fb2"), storedFileIn(directory, "Lem/solaris.fb2"))
        assertEquals(
            File(directory, "solaris.fb2"),
            storedFileIn(directory, "/storage/emulated/0/Books/solaris.fb2")
        )
    }

    /** Whatever survives the cleaning is still asked to sit in the directory itself. */
    @Test
    fun `the result is always a direct child of the directory`() {
        listOf(
            "solaris.fb2",
            "../../../x.epub",
            "..//x.epub",
            "a/../../b.epub",
            "./x.epub"
        ).forEach { name ->
            val stored = storedFileIn(directory, name)
            assertNotNull("$name was refused outright", stored)
            assertEquals(
                "$name escaped the directory",
                directory.canonicalFile,
                stored!!.canonicalFile.parentFile
            )
        }
    }

    @Test
    fun `a name that names a directory rather than a file is refused`() {
        assertNull(storedFileIn(directory, ".."))
        assertNull(storedFileIn(directory, "."))
        assertNull(storedFileIn(directory, "books/.."))
        assertNull(storedFileIn(directory, "books/"))
    }

    @Test
    fun `a name that is not a name is refused`() {
        assertNull(storedFileIn(directory, ""))
        assertNull(storedFileIn(directory, "   "))
        assertNull(storedFileIn(directory, "/"))
    }

    /** A name the file system itself will not take, rather than one it would misplace. */
    @Test
    fun `a name holding a NUL is refused`() {
        assertNull(storedFileIn(directory, "solaris\u0000.fb2"))
    }

    /**
     * `filesDir` is reached through a symlink on a device (`/data/user/0/<pkg>`
     * for `/data/data/<pkg>`), and on macOS the temporary directory is one too,
     * so the containment check has to compare canonical paths on both sides or it
     * would refuse every name there is.
     */
    @Test
    fun `a directory reached through a symlink still takes ordinary names`() {
        val real = temporaryFolder.newFolder("real", "7")
        val link = File(temporaryFolder.root, "link")
        java.nio.file.Files.createSymbolicLink(link.toPath(), File(real.parent).toPath())

        val through = File(link, "7")
        assertEquals(File(through, "solaris.fb2"), storedFileIn(through, "solaris.fb2"))
    }
}
