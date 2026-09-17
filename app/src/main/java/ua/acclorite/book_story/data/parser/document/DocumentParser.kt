/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * How many items of a phase run between two [yield] calls.
 *
 * Yielding once per line cost a quarter of the whole parse (#39): the work
 * itself is microseconds and the round-trip through the dispatcher is ~166 µs,
 * moving the coroutine between worker threads each time. Cancellation is not
 * what paid for that — `ensureActive` still runs on every item and throws just
 * as promptly; a yield only lets *other* coroutines in, and at 64 items that is
 * still well under 50 ms of work between turns.
 */
private const val YIELD_INTERVAL = 64

class DocumentParser @Inject constructor() {

    /**
     * Parses a document into [ReaderText] by walking its DOM once (#29): see
     * [DocumentWalk] for what becomes what.
     *
     * @param includeChapter Whether the first visible line becomes the chapter
     *   title when the document has no heading of its own.
     * @param sectionTitles Whether the `<title>` of a `<body>`/`<section>` is a
     *   chapter heading — FB2.
     * @return The document's entries; empty when it yields nothing, or when a
     *   chapter was required and none was found.
     */
    suspend fun parseDocument(
        document: Document,
        zipFile: ZipFile? = null,
        imageEntries: List<ZipEntry>? = null,
        base64Images: Map<String, String>? = null,
        includeChapter: Boolean = true,
        sectionTitles: Boolean = false,
        keepImageBytes: Boolean = true
    ): List<ReaderText> = coroutineScope {
        yield()

        // Images decode in parallel while the text is being walked
        val imageJobs = mutableMapOf<String, Deferred<ReaderImage?>>()
        fun decode(src: String) = imageJobs.getOrPut(src) {
            async(Dispatchers.Default) {
                loadImage(src, zipFile, imageEntries, base64Images, keepImageBytes)
            }
        }

        val walk = DocumentWalk(
            includeChapter = includeChapter,
            sectionTitles = sectionTitles,
            image = { element ->
                when (element.normalName()) {
                    "img" -> element.attr("src")
                        .trim()
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf { src ->
                            src.containsVisibleText() && imageEntries.holds(src)
                        }

                    // FB2 <image> references a <binary> by id; SVG's by path
                    else -> element.attr("xlink:href")
                        .ifBlank { element.attr("l:href") }
                        .ifBlank { element.attr("href") }
                        .trim()
                        .removePrefix("#")
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf { src ->
                            src.containsVisibleText() && (
                                    base64Images?.containsKey(src) == true ||
                                            imageEntries.holds(src)
                                    )
                        }
                }?.let { src -> decode(src) }
            }
        )

        val body = document.selectFirst("body").run { this ?: document.body() }
        timed("      walk") { walk.run(body, YIELD_INTERVAL) }

        val readerText = ArrayList<ReaderText>(walk.entries.size)
        walk.entries.forEach { entry ->
            when (entry) {
                is DocumentWalk.Entry.Ready -> readerText.add(entry.text)
                is DocumentWalk.Entry.Picture -> entry.image.await()?.let { image ->
                    readerText.add(ReaderText.Image(image, entry.caption))
                }
            }
        }

        yield()

        // "Did this document yield anything", not "does it have text": a page
        // holding only an image — every Calibre-made cover is exactly that — is
        // not an empty document, and dropping it takes the image with it.
        if (
            readerText.isEmpty() ||
            (includeChapter && readerText.none { it is ReaderText.Chapter })
        ) {
            return@coroutineScope emptyList()
        }

        readerText
    }

    /**
     * Renders a footnote body (an FB2 <section> from <body name="notes">) to a
     * formatted [AnnotatedString]: the same walk as the text, its titles left
     * out, its lines kept as paragraphs separated by a blank line. References
     * inside a note stay plain text — a note has no text of its own to jump in.
     */
    suspend fun parseNote(section: Element): AnnotatedString {
        val walk = DocumentWalk(
            includeChapter = false,
            sectionTitles = false,
            dropTitles = true,
            links = false
        )
        walk.run(section, YIELD_INTERVAL)

        val paragraphs = walk.entries.flatMap { entry ->
            when (val text = (entry as? DocumentWalk.Entry.Ready)?.text) {
                is ReaderText.Text -> listOf(text)
                is ReaderText.Poem -> text.lines
                else -> emptyList()
            }
        }.filter { paragraph -> paragraph.line.text.containsVisibleText() }

        return buildAnnotatedString {
            paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append("\n\n")
                append(paragraph.line)
            }
        }
    }

    private fun List<ZipEntry>?.holds(src: String): Boolean =
        this?.any { image -> src == image.name.substringAfterLast(File.separator).lowercase() } == true

    /**
     * Loading the encoded image bytes from a [ZipFile] entry (EPUB)
     * or a Base64 <binary> (FB2). Only the image bounds are decoded here,
     * pixels are decoded lazily when the reader shows the image.
     *
     * @return Null if the image was not found or is not decodable.
     */
    private fun loadImage(
        src: String,
        zipFile: ZipFile?,
        imageEntries: List<ZipEntry>?,
        base64Images: Map<String, String>?,
        keepImageBytes: Boolean
    ): ReaderImage? {
        return try {
            val bytes = base64Images?.get(src)?.let { encoded ->
                Base64.decode(encoded, Base64.DEFAULT)
            } ?: imageEntries?.find { image ->
                src == image.name.substringAfterLast(File.separator).lowercase()
            }?.let { imageEntry ->
                zipFile?.getInputStream(imageEntry)?.use { it.readBytes() }
            } ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Identity always comes from the real bytes, so an image keeps the
            // same id whether or not they are kept — the parse stays cacheable.
            val checksum = CRC32().apply { update(bytes) }.value
            ReaderImage(
                id = "$src-${bytes.size}-$checksum",
                src = src,
                bytes = if (keepImageBytes) bytes else ByteArray(0),
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            e.printStackTrace()
            null
        }
    }
}