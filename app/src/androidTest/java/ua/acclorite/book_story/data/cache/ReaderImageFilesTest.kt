/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Checks for the transient per-session store behind the reader's images. */
@RunWith(AndroidJUnit4::class)
class ReaderImageFilesTest {

    private lateinit var app: Application
    private lateinit var root: File
    private lateinit var files: ReaderImageFiles

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        root = File(app.cacheDir, "reader_images")
        root.deleteRecursively()
        files = ReaderImageFiles(app)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun writeStoresTheBytesAndHandsBackTheFile() {
        val file = files.write(bookId = 1, src = "images/cover.jpg", bytes = byteArrayOf(1, 2, 3))

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test
    fun rewritingASrcReplacesItInPlace() {
        val first = files.write(1, "a.jpg", byteArrayOf(1))
        val second = files.write(1, "a.jpg", byteArrayOf(2, 2))

        assertEquals(first, second)
        assertArrayEquals(byteArrayOf(2, 2), second!!.readBytes())
    }

    @Test
    fun nothingIsLeftBehindBesidesTheImages() {
        files.write(1, "a.jpg", byteArrayOf(1))

        // The write goes through a temp file; it must not survive the rename.
        val written = sessionDir().walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, written.size)
        assertFalse(written.single().name.endsWith(".tmp"))
    }

    @Test
    fun booksDoNotShareFilesEvenWhenTheirSrcsCollide() {
        val one = files.write(1, "images/cover.jpg", byteArrayOf(1))
        val two = files.write(2, "images/cover.jpg", byteArrayOf(2))

        assertNotEquals(one, two)
        assertArrayEquals(byteArrayOf(1), one!!.readBytes())
        assertArrayEquals(byteArrayOf(2), two!!.readBytes())
    }

    @Test
    fun keepOnlyDropsEveryOtherBook() {
        val kept = files.write(1, "a.jpg", byteArrayOf(1))
        val dropped = files.write(2, "a.jpg", byteArrayOf(2))
        val alsoDropped = files.write(3, "a.jpg", byteArrayOf(3))

        files.keepOnly(bookId = 1)

        assertTrue(kept!!.exists())
        assertFalse(dropped!!.exists())
        assertFalse(alsoDropped!!.exists())
    }

    @Test
    fun keepOnlyAnUnknownBookLeavesNothingBehind() {
        val written = files.write(1, "a.jpg", byteArrayOf(1))

        files.keepOnly(bookId = 7)

        assertFalse(written!!.exists())
    }

    @Test
    fun existingFindsWhatWasWrittenForTheBook() {
        files.write(1, "a.jpg", byteArrayOf(1))
        files.write(1, "b.jpg", byteArrayOf(2))
        files.write(2, "c.jpg", byteArrayOf(3))

        val found = files.existing(bookId = 1, srcs = setOf("a.jpg", "b.jpg", "c.jpg", "d.jpg"))

        assertEquals(setOf("a.jpg", "b.jpg"), found.keys)
        assertArrayEquals(byteArrayOf(1), found.getValue("a.jpg").readBytes())
    }

    @Test
    fun existingIsEmptyForABookThatWroteNothing() {
        files.write(1, "a.jpg", byteArrayOf(1))

        assertTrue(files.existing(bookId = 2, srcs = setOf("a.jpg")).isEmpty())
    }

    @Test
    fun existingIgnoresAnEmptyFile() {
        val file = files.write(1, "a.jpg", byteArrayOf(1))!!
        file.writeBytes(ByteArray(0))

        assertTrue(files.existing(bookId = 1, srcs = setOf("a.jpg")).isEmpty())
    }

    @Test
    fun aReopenedBookFindsTheFilesItLeftBehind() {
        // What leaving and re-entering a book looks like from here: nothing is
        // dropped on the way out, and on the way back in its own files survive.
        val written = files.write(1, "a.jpg", byteArrayOf(1, 2, 3))
        files.keepOnly(bookId = 1)

        val found = files.existing(1, setOf("a.jpg"))

        assertEquals(written, found["a.jpg"])
        assertArrayEquals(byteArrayOf(1, 2, 3), found.getValue("a.jpg").readBytes())
    }

    @Test
    fun sweepDeletesOtherRunsAndSparesTheLiveOne() {
        val mine = files.write(1, "a.jpg", byteArrayOf(1))
        // What a run that never got to clean up after itself would have left.
        val stale = File(root, "0000-a-previous-run")
        assertTrue(File(stale, "1").mkdirs())
        val staleImage = File(stale, "1/blob").apply { writeBytes(byteArrayOf(9)) }

        files.sweep()

        assertFalse(staleImage.exists())
        assertFalse(stale.exists())
        assertTrue(mine!!.exists())
    }

    @Test
    fun aSecondRunSweepsTheFirstOneAway() {
        val previous = files.write(1, "a.jpg", byteArrayOf(1))

        // A new instance stands in for the next process: its own session dir, and
        // a sweep that takes everything else with it.
        val next = ReaderImageFiles(app)
        val current = next.write(1, "a.jpg", byteArrayOf(2))
        next.sweep()

        assertFalse(previous!!.exists())
        assertTrue(current!!.exists())
    }

    private fun sessionDir(): File = root.listFiles()!!.single()
}
