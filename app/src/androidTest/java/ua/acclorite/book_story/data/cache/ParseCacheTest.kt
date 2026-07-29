/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class ParseCacheTest {

    private lateinit var app: Application
    private lateinit var cache: ParseCache

    private val sample = ParsedText(
        text = listOf(
            ReaderText.Chapter(title = "Chapter"),
            ReaderText.Text(AnnotatedString("Hello world")),
            ReaderText.Text(AnnotatedString("Second line"))
        ),
        notes = mapOf("n" to AnnotatedString("a note"))
    )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        cache = ParseCache(app)
        cache.clear()
    }

    @Test
    fun writeThenReadHit() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        val restored = cache.read(PATH, SIZE, MODIFIED)

        assertNotNull(restored)
        assertEquals(3, restored!!.text.size)
        assertEquals("Hello world", (restored.text[1] as ReaderText.Text).line.text)
        assertEquals(setOf("n"), restored.notes.keys)
    }

    @Test
    fun missBeforeWrite() {
        assertNull(cache.read("never-written", SIZE, MODIFIED))
    }

    @Test
    fun missOnDifferentIdentity() {
        cache.write(PATH, SIZE, MODIFIED, sample)

        assertNull("different size is a miss", cache.read(PATH, SIZE + 1, MODIFIED))
        assertNull("different mtime is a miss", cache.read(PATH, SIZE, MODIFIED + 1))
        assertNull("different path is a miss", cache.read("$PATH.other", SIZE, MODIFIED))
    }

    @Test
    fun removeThenMiss() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        cache.remove(PATH, SIZE, MODIFIED)

        assertNull(cache.read(PATH, SIZE, MODIFIED))
    }

    @Test
    fun clearRemovesAll() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        cache.write("$PATH.2", SIZE, MODIFIED, sample)

        cache.clear()

        assertNull(cache.read(PATH, SIZE, MODIFIED))
        assertNull(cache.read("$PATH.2", SIZE, MODIFIED))
    }

    @Test
    fun totalSizeBytesSumsEntries() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        cache.write("$PATH.2", SIZE, MODIFIED, sample)

        val one = cache.entrySizeBytes(PATH, SIZE, MODIFIED)
        assertEquals(2 * one, cache.totalSizeBytes())
    }

    @Test
    fun writeEvictsOldestOverCap() {
        // Two entries, A older than B, then a capped write of C.
        cache.write(PATH, SIZE, MODIFIED, sample)
        val fileA = entries().single()
        fileA.setLastModified(1_000)
        cache.write("$PATH.2", SIZE, MODIFIED, sample)
        (entries() - fileA).single().setLastModified(2_000)

        val one = cache.entrySizeBytes(PATH, SIZE, MODIFIED)
        // Cap holds two entries; writing a third must evict exactly the oldest (A).
        cache.write("$PATH.3", SIZE, MODIFIED, sample, maxBytes = 2 * one)

        assertNull("oldest is evicted", cache.read(PATH, SIZE, MODIFIED))
        assertNotNull(cache.read("$PATH.2", SIZE, MODIFIED))
        assertNotNull("just-written entry is never evicted", cache.read("$PATH.3", SIZE, MODIFIED))
    }

    @Test
    fun readTouchesEntryForLru() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        val fileA = entries().single()
        fileA.setLastModified(1_000)
        cache.write("$PATH.2", SIZE, MODIFIED, sample)
        (entries() - fileA).single().setLastModified(2_000)

        // Reading A makes it most-recently-used, so B becomes the eviction target.
        cache.read(PATH, SIZE, MODIFIED)

        val one = cache.entrySizeBytes(PATH, SIZE, MODIFIED)
        cache.write("$PATH.3", SIZE, MODIFIED, sample, maxBytes = 2 * one)

        assertNotNull("touched entry survives", cache.read(PATH, SIZE, MODIFIED))
        assertNull("untouched entry is evicted", cache.read("$PATH.2", SIZE, MODIFIED))
        assertNotNull(cache.read("$PATH.3", SIZE, MODIFIED))
    }

    @Test
    fun trimToSizeEvictsOldest() {
        cache.write(PATH, SIZE, MODIFIED, sample)
        val fileA = entries().single()
        fileA.setLastModified(1_000)
        cache.write("$PATH.2", SIZE, MODIFIED, sample)
        (entries() - fileA).single().setLastModified(2_000)

        val one = cache.entrySizeBytes(PATH, SIZE, MODIFIED)
        cache.trimToSize(one) // room for a single entry — the newest.

        assertNull(cache.read(PATH, SIZE, MODIFIED))
        assertNotNull(cache.read("$PATH.2", SIZE, MODIFIED))
    }

    @Test
    fun corruptEntryIsTreatedAsMiss() {
        cache.write(PATH, SIZE, MODIFIED, sample)

        // Corrupt the stored text blob on disk.
        val dir = File(app.cacheDir, "parsed_books")
        val entry = dir.listFiles()!!.first { it.isDirectory }
        File(entry, "text").writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

        assertNull(cache.read(PATH, SIZE, MODIFIED))
        // A corrupt entry is dropped (whole book dir), not left behind.
        assertNull(dir.listFiles()?.firstOrNull { it.name == entry.name })
    }

    @Test
    fun entryFromAnOlderFormatVersionIsTreatedAsMiss() {
        cache.write(PATH, SIZE, MODIFIED, sample)

        // Restamp the entry with the previous format version — what every
        // device already holds after ParsedTextCodec.VERSION is bumped. The
        // version int is the first four bytes of the blob.
        val dir = File(app.cacheDir, "parsed_books")
        val entry = dir.listFiles()!!.first { it.isDirectory }
        RandomAccessFile(File(entry, "text"), "rw").use { file ->
            file.writeInt(ParsedTextCodec.VERSION - 1)
        }

        assertNull(cache.read(PATH, SIZE, MODIFIED))
        // The stale entry is dropped, so the book is simply re-parsed.
        assertNull(dir.listFiles()?.firstOrNull { it.name == entry.name })
    }

    @Test
    fun writeImagesThenGetTheirBlobFiles() {
        val images = mapOf(
            "cover.jpg" to byteArrayOf(1, 2, 3, 4),
            "fig1.png" to byteArrayOf(5, 6, 7)
        )
        cache.write(PATH, SIZE, MODIFIED, sample, images = images)

        val blobs = cache.imageBlobFiles(PATH, SIZE, MODIFIED, images.keys)
        assertEquals(setOf("cover.jpg", "fig1.png"), blobs.keys)
        // Files, not bytes — the reader hands them straight to the image loader.
        assertArrayEquals(images["cover.jpg"], blobs["cover.jpg"]!!.readBytes())
        assertArrayEquals(images["fig1.png"], blobs["fig1.png"]!!.readBytes())
    }

    @Test
    fun imageBlobFilesIgnoresAbsentSrcs() {
        cache.write(
            PATH, SIZE, MODIFIED, sample,
            images = mapOf("present.jpg" to byteArrayOf(9))
        )

        val blobs = cache.imageBlobFiles(PATH, SIZE, MODIFIED, setOf("present.jpg", "absent.jpg"))
        assertEquals(setOf("present.jpg"), blobs.keys)
    }

    @Test
    fun writeImageBlobReturnsTheFileItWrote() {
        cache.write(PATH, SIZE, MODIFIED, sample)

        val blob = cache.writeImageBlob(PATH, SIZE, MODIFIED, "late.jpg", byteArrayOf(7, 7))

        assertNotNull(blob)
        assertArrayEquals(byteArrayOf(7, 7), blob!!.readBytes())
        assertEquals(
            setOf("late.jpg"),
            cache.imageBlobFiles(PATH, SIZE, MODIFIED, setOf("late.jpg")).keys
        )
    }

    @Test
    fun writeImageBlobRequiresExistingBook() {
        // No text entry written first → blobs must not be persisted on their own.
        val blob = cache.writeImageBlob(PATH, SIZE, MODIFIED, "x.jpg", byteArrayOf(1))

        assertNull(blob)
        assertNull(cache.read(PATH, SIZE, MODIFIED))
        assertEquals(
            emptySet<String>(),
            cache.imageBlobFiles(PATH, SIZE, MODIFIED, setOf("x.jpg")).keys
        )
    }

    @Test
    fun capCountsImageBlobsWhenEvicting() {
        // A carries a large blob; B is text-only. A is older, so a capped write
        // of C must evict A once its blob is counted in the total.
        val bigBlob = ByteArray(4096) { 1 }
        cache.write(PATH, SIZE, MODIFIED, sample, images = mapOf("big.jpg" to bigBlob))
        val fileA = entries().single()
        fileA.setLastModified(1_000)

        val textOnly = cache.entrySizeBytes("$PATH.2", SIZE, MODIFIED) // 0, not written yet
        cache.write("$PATH.2", SIZE, MODIFIED, sample)
        (entries() - fileA).single().setLastModified(2_000)

        val bText = cache.entrySizeBytes("$PATH.2", SIZE, MODIFIED)
        assertEquals(0, textOnly)
        // Cap = two text-only books' worth; A (text + 4 KB blob) blows past it and,
        // being oldest, is the one evicted.
        cache.write("$PATH.3", SIZE, MODIFIED, sample, maxBytes = 2 * bText)

        assertNull("book with the large blob is evicted", cache.read(PATH, SIZE, MODIFIED))
        assertNotNull(cache.read("$PATH.2", SIZE, MODIFIED))
        assertNotNull(cache.read("$PATH.3", SIZE, MODIFIED))
    }

    /** Committed cache book directories on disk. */
    private fun entries(): List<File> =
        File(app.cacheDir, "parsed_books")
            .listFiles()?.filter { it.isDirectory } ?: emptyList()

    private companion object {
        const val PATH = "/tree/primary:Books/document/primary:Books%2Fbook.fb2"
        const val SIZE = 123_456L
        const val MODIFIED = 1_700_000_000_000L
    }
}
