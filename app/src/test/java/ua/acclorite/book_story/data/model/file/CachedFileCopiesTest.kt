/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.model.file

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The start-up cleanup of book copies must not take a copy made after it began:
 * a cold-start "Open with" copies its book while the cleanup is still running.
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
}
