/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BookImageStoreTest {

    private lateinit var store: BookImageStore

    @Before
    fun setUp() {
        store = BookImageStore()
    }

    @Test
    fun unknownSrcReadsAsLoading() {
        assertTrue(store["nothing.jpg"] is BookImage.Loading)
    }

    @Test
    fun resetTracksEveryImageAsPending() {
        store.reset(listOf("a.jpg", "b.jpg"))

        assertTrue(store["a.jpg"] is BookImage.Loading)
        assertTrue(store["b.jpg"] is BookImage.Loading)
        assertEquals(setOf("a.jpg", "b.jpg"), store.pending())
    }

    @Test
    fun putPublishesTheFileAndClearsPending() {
        store.reset(listOf("a.jpg", "b.jpg"))
        store.put("a.jpg", BookImage.Ready.InFile(File("/tmp/a")))

        val image = store["a.jpg"]
        assertTrue(image is BookImage.Ready.InFile)
        assertEquals(File("/tmp/a"), (image as BookImage.Ready.InFile).file)
        assertEquals(setOf("b.jpg"), store.pending())
    }

    @Test
    fun anInMemoryImageCountsAsReadyToo() {
        store.reset(listOf("a.jpg"))
        store.put("a.jpg", BookImage.Ready.InMemory(byteArrayOf(1, 2, 3)))

        val image = store["a.jpg"]
        assertTrue(image is BookImage.Ready.InMemory)
        assertArrayEquals(byteArrayOf(1, 2, 3), (image as BookImage.Ready.InMemory).bytes)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun finishMarksUnresolvedAsMissing() {
        store.reset(listOf("a.jpg", "b.jpg"))
        store.put("a.jpg", BookImage.Ready.InFile(File("/tmp/a")))

        store.finish()

        assertTrue(store["a.jpg"] is BookImage.Ready)
        assertTrue(store["b.jpg"] is BookImage.Missing)
    }

    @Test
    fun missingStaysPendingSoItCanBeRetried() {
        store.reset(listOf("a.jpg"))
        store.finish()

        assertEquals(setOf("a.jpg"), store.pending())
    }

    @Test
    fun resetDiscardsThePreviousBook() {
        store.reset(listOf("a.jpg"))
        store.put("a.jpg", BookImage.Ready.InFile(File("/tmp/a")))

        store.reset(listOf("c.jpg"))

        assertTrue(store["a.jpg"] is BookImage.Loading)
        assertEquals(setOf("c.jpg"), store.pending())
    }
}
