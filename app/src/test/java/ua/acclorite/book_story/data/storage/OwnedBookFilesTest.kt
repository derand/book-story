/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Replacing a book's copy: the old one has to survive anything short of a
 * whole new one.
 *
 * Robolectric for the `Application` whose `filesDir` the store lives in.
 */
@RunWith(RobolectricTestRunner::class)
class OwnedBookFilesTest {

    private val application = RuntimeEnvironment.getApplication()
    private val files = OwnedBookFiles(application)
    private val directory: File
        get() = File(application.filesDir, "owned_books/7")

    private lateinit var kept: File

    @Before
    fun storeTheCopyTheBookHas() {
        kept = requireNotNull(files.store(7, "a-book.fb2", 1_000L) { "old text".byteInputStream() })
    }

    @Test
    fun `a source that fails halfway leaves the copy the book has`() {
        val failing = object : InputStream() {
            private var left = 4
            override fun read(): Int =
                if (left-- > 0) 'x'.code else throw IOException("The provider went away.")
        }

        assertNull(files.store(7, "a-book.fb2", 2_000L) { failing })

        assertEquals("old text", kept.readText())
        assertEquals(listOf("a-book.fb2"), directory.list()?.toList())
    }

    @Test
    fun `a source that will not open leaves the copy the book has`() {
        assertNull(files.store(7, "a-book.fb2", 2_000L) { null })

        assertEquals("old text", kept.readText())
        assertEquals(listOf("a-book.fb2"), directory.list()?.toList())
    }

    @Test
    fun `a whole new copy replaces the old one and carries the source's date`() {
        val stored = files.store(7, "a-book.fb2", 2_000L) { "new text".byteInputStream() }

        assertEquals(kept, stored)
        assertEquals("new text", kept.readText())
        assertEquals(2_000L, kept.lastModified())
        assertEquals(listOf("a-book.fb2"), directory.list()?.toList())
    }

    @Test
    fun `a copy under another name waits for keepOnly to drop the old one`() {
        val stored = requireNotNull(
            files.store(7, "a-book.epub", 2_000L) { "new text".byteInputStream() }
        )

        assertEquals("old text", kept.readText())

        files.keepOnly(7, stored.absolutePath)

        assertEquals(listOf("a-book.epub"), directory.list()?.toList())
    }
}
