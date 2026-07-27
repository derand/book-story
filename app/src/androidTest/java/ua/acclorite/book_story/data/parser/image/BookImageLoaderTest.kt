/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.image

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.model.file.CachedFileBuilder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BookImageLoaderTest {

    private lateinit var app: Application
    private lateinit var dir: File
    private lateinit var loader: BookImageLoader

    private val cover = byteArrayOf(1, 2, 3, 4, 5)
    private val page = ByteArray(300) { (it % 251).toByte() }
    private val extra = byteArrayOf(9, 9, 9)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        dir = File(app.cacheDir, "image-loader-test").apply {
            deleteRecursively()
            mkdirs()
        }
        loader = BookImageLoader()
    }

    @Test
    fun loadsRequestedFb2BinariesOnly() {
        val file = fb2(
            binary("cover.jpg", cover),
            binary("page1.png", page),
            binary("extra.jpg", extra)
        )

        val loaded = loadAll(file, setOf("cover.jpg", "page1.png"))

        assertEquals(listOf("cover.jpg", "page1.png"), loaded.map { it.first })
        assertArrayEquals(cover, loaded[0].second)
        assertArrayEquals(page, loaded[1].second)
    }

    @Test
    fun reportsEachImageAsItIsRead() {
        val file = fb2(binary("a.jpg", cover), binary("b.jpg", page))

        // Progressive delivery is the point: the reader shows each image as soon
        // as it is ready rather than waiting for the whole file.
        val seen = mutableListOf<String>()
        loader.loadImages(cachedFile(file), setOf("a.jpg", "b.jpg")) { src, _ ->
            seen.add(src)
            if (seen.size == 1) assertEquals(listOf("a.jpg"), seen)
        }

        assertEquals(listOf("a.jpg", "b.jpg"), seen)
    }

    @Test
    fun ignoresBinariesThatWereNotAskedFor() {
        val file = fb2(binary("cover.jpg", cover))

        val loaded = loadAll(file, setOf("absent.jpg"))

        assertTrue(loaded.isEmpty())
    }

    @Test
    fun matchesBinaryIdCaseInsensitively() {
        val file = fb2(binary("Cover.JPG", cover))

        val loaded = loadAll(file, setOf("cover.jpg"))

        assertEquals(listOf("cover.jpg"), loaded.map { it.first })
        assertArrayEquals(cover, loaded[0].second)
    }

    @Test
    fun handlesWrappedBase64() {
        // Real FB2 writers wrap base64 at ~76 columns.
        val wrapped = Base64.encodeToString(page, Base64.NO_WRAP)
            .chunked(76)
            .joinToString("\n")
        val file = fb2("""<binary id="page1.png" content-type="image/png">$wrapped</binary>""")

        val loaded = loadAll(file, setOf("page1.png"))

        assertArrayEquals(page, loaded.single().second)
    }

    @Test
    fun handlesSingleQuotedIds() {
        val encoded = Base64.encodeToString(cover, Base64.NO_WRAP)
        val file = fb2("""<binary id='cover.jpg' content-type='image/jpeg'>$encoded</binary>""")

        assertArrayEquals(cover, loadAll(file, setOf("cover.jpg")).single().second)
    }

    @Test
    fun skipsSelfClosingBinaryWithoutSwallowingTheNextOne() {
        val file = fb2("""<binary id="broken.jpg"/>""", binary("cover.jpg", cover))

        val loaded = loadAll(file, setOf("broken.jpg", "cover.jpg"))

        assertEquals(listOf("cover.jpg"), loaded.map { it.first })
        assertArrayEquals(cover, loaded[0].second)
    }

    @Test
    fun ignoresElementsMerelyStartingLikeBinary() {
        val file = fb2("""<binaryish id="cover.jpg">not base64</binaryish>""", binary("cover.jpg", cover))

        assertArrayEquals(cover, loadAll(file, setOf("cover.jpg")).single().second)
    }

    @Test
    fun loadsEpubEntriesByBasename() {
        val file = File(dir, "book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
            zip.write(cover)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/images/page1.png"))
            zip.write(page)
            zip.closeEntry()
        }

        val loaded = loadAll(file, setOf("cover.jpg", "page1.png")).toMap()

        assertEquals(setOf("cover.jpg", "page1.png"), loaded.keys)
        assertArrayEquals(cover, loaded["cover.jpg"])
        assertArrayEquals(page, loaded["page1.png"])
    }

    @Test
    fun formatsWithoutImageBytesLoadNothing() {
        val file = File(dir, "book.txt").apply { writeText("plain text") }

        assertTrue(loadAll(file, setOf("cover.jpg")).isEmpty())
    }

    private fun loadAll(file: File, srcs: Set<String>): List<Pair<String, ByteArray>> {
        val loaded = mutableListOf<Pair<String, ByteArray>>()
        loader.loadImages(cachedFile(file), srcs) { src, bytes -> loaded.add(src to bytes) }
        return loaded
    }

    private fun cachedFile(file: File) = CachedFile(
        context = app,
        uri = Uri.fromFile(file),
        builder = CachedFileBuilder(
            name = file.name,
            path = file.path,
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false
        )
    )

    private fun binary(id: String, bytes: ByteArray): String =
        """<binary id="$id" content-type="image/jpeg">""" +
                Base64.encodeToString(bytes, Base64.NO_WRAP) +
                "</binary>"

    private fun fb2(vararg binaries: String): File {
        val file = File(dir, "book.fb2")
        file.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <FictionBook>
                <body><section><p>Text with an <image l:href="#cover.jpg"/> in it.</p></section></body>
                ${binaries.joinToString("\n")}
            </FictionBook>
            """.trimIndent()
        )
        return file
    }
}
