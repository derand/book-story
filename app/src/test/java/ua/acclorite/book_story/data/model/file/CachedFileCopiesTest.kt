/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.model.file

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The lifetime of the book copies [CachedFile.rawFile] makes. The start-up
 * cleanup must not take a copy made after it began (a cold-start "Open with"
 * copies its book while the cleanup is still running), and a released copy must
 * be gone without touching anything that is not a copy.
 */
@RunWith(RobolectricTestRunner::class)
class CachedFileCopiesTest {

    private lateinit var context: Context
    private val copies get() = File(context.cacheDir, "raw_copies")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun copy(name: String) = File(copies, name).apply {
        parentFile!!.mkdirs()
        writeText(name)
    }

    @Test
    fun `a copy made after retiring survives the deletion`() {
        copy("old")

        CachedFile.retireCopies(context)
        val fresh = copy("fresh")
        CachedFile.deleteRetiredCopies(context)

        assertTrue(fresh.exists())
        assertEquals(listOf("fresh"), copies.list()!!.toList())
        assertEquals(listOf("raw_copies"), context.cacheDir.list()!!.toList())
    }

    @Test
    fun `the rest of the cache is left alone`() {
        copy("old")
        val parsed = File(context.cacheDir, "parsed_books/entry").apply {
            parentFile!!.mkdirs()
            writeText("x")
        }

        CachedFile.retireCopies(context)
        CachedFile.deleteRetiredCopies(context)

        assertTrue(parsed.exists())
        assertFalse(copies.exists())
    }

    @Test
    fun `retiring with no copies does nothing`() {
        CachedFile.retireCopies(context)
        CachedFile.deleteRetiredCopies(context)

        assertEquals(0, context.cacheDir.list()!!.size)
    }

    /** A file behind a URI, as a provider's book is: [CachedFile.rawFile] copies it. */
    private fun providerFile(): CachedFile {
        val source = File(context.filesDir, "book.epub").apply { writeText("book") }
        return CachedFile(
            context,
            Uri.fromFile(source),
            CachedFileBuilder(
                name = source.name,
                path = source.path,
                size = source.length(),
                lastModified = source.lastModified(),
                isDirectory = false
            )
        )
    }

    @Test
    fun `a released copy is deleted`() {
        val file = providerFile()
        val copy = file.rawFile

        assertNotNull(copy)
        assertEquals(File(copies, copy!!.name), copy)
        file.releaseRawFile()

        assertFalse(copy.exists())
        assertTrue(File(context.filesDir, "book.epub").exists())
    }

    @Test
    fun `releasing a file never read makes no copy`() {
        providerFile().releaseRawFile()

        assertFalse(copies.exists())
    }

    @Test
    fun `releasing a file the app owns leaves it alone`() {
        val owned = File(context.filesDir, "owned.epub").apply { writeText("book") }
        val file = CachedFileCompat.fromFile(context, owned)

        assertEquals(owned, file.rawFile)
        file.releaseRawFile()

        assertTrue(owned.exists())
    }
}
