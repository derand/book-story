/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    fun putPublishesBytesAndClearsPending() {
        store.reset(listOf("a.jpg", "b.jpg"))
        store.put("a.jpg", byteArrayOf(1, 2, 3))

        val image = store["a.jpg"]
        assertTrue(image is BookImage.Ready)
        assertArrayEquals(byteArrayOf(1, 2, 3), (image as BookImage.Ready).bytes)
        assertEquals(setOf("b.jpg"), store.pending())
    }

    @Test
    fun finishMarksUnresolvedAsMissing() {
        store.reset(listOf("a.jpg", "b.jpg"))
        store.put("a.jpg", byteArrayOf(1))

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
        store.put("a.jpg", byteArrayOf(1))

        store.reset(listOf("c.jpg"))

        assertTrue(store["a.jpg"] is BookImage.Loading)
        assertEquals(setOf("c.jpg"), store.pending())
    }
}
