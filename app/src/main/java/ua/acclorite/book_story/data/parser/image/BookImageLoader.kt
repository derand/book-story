/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.image

import android.util.Base64
import ua.acclorite.book_story.core.data.ExtensionsData
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookImageLoader"

/** Buffer of the FB2 scanner; sized so a large book is a few hundred reads. */
private const val SCAN_BUFFER = 64 * 1024

/** `id` of an FB2 `<binary>`, single- or double-quoted. */
private val BINARY_ID_REGEX = Regex("""id\s*=\s*("([^"]*)"|'([^']*)')""")

/**
 * Re-loads the encoded bytes of book images by their `src` lookup key, straight
 * from the source file — without re-parsing the text. Used to fill in images on a
 * parse-cache hit (the cache stores image metadata only). The `src` matching
 * mirrors [ua.acclorite.book_story.data.parser.document.DocumentParser]:
 * FB2 keys are the lowercased `<binary>` id, EPUB keys the lowercased basename.
 *
 * Images are handed over one by one as they are resolved, so the reader can show
 * each as soon as it is ready instead of waiting for the whole file. FB2 is
 * scanned as a stream rather than parsed into a DOM — a book can hold tens of MB
 * of base64, and only one image is held in memory at a time.
 *
 * Only FB2 and EPUB carry image bytes; every other format loads nothing.
 */
@Singleton
class BookImageLoader @Inject constructor() {

    /**
     * Resolves the bytes of [srcs] from the book file, invoking [onImage] for
     * each one as soon as it is read. Stops early once every src is resolved.
     * Unresolvable srcs are simply never reported.
     */
    fun loadImages(
        cachedFile: CachedFile,
        srcs: Set<String>,
        onImage: (src: String, bytes: ByteArray) -> Unit
    ) {
        if (srcs.isEmpty()) return
        val format = ExtensionsData.formatOf(cachedFile.name)
        when (format) {
            ".fb2" -> loadFromFb2(cachedFile, srcs, onImage)
            ".epub" -> loadFromEpub(cachedFile, srcs, onImage)
        }
    }

    private fun loadFromFb2(
        cachedFile: CachedFile,
        srcs: Set<String>,
        onImage: (String, ByteArray) -> Unit
    ) {
        try {
            cachedFile.openInputStream()?.use { stream ->
                scanFb2Binaries(stream, srcs, onImage)
            }
        } catch (e: Exception) {
            logE(TAG, "Could not load FB2 images: ${e.message}")
        }
    }

    private fun loadFromEpub(
        cachedFile: CachedFile,
        srcs: Set<String>,
        onImage: (String, ByteArray) -> Unit
    ) {
        val rawFile = cachedFile.rawFile
        if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return
        try {
            ZipFile(rawFile).use { zip ->
                val entries = zip.entries().toList()
                srcs.forEach { src ->
                    val entry = entries.find { entry ->
                        src == entry.name.substringAfterLast(File.separator).lowercase()
                    } ?: return@forEach
                    try {
                        onImage(src, zip.getInputStream(entry).use { it.readBytes() })
                    } catch (e: Exception) {
                        logE(TAG, "Could not read EPUB image [$src]: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logE(TAG, "Could not load EPUB images: ${e.message}")
        }
    }

    /**
     * Walks [stream] looking for `<binary>` elements, decoding only those whose
     * id is in [srcs]. The bytes of an unwanted image are skipped without being
     * collected, so a book full of images that nobody asked for costs a read.
     */
    private fun scanFb2Binaries(
        stream: InputStream,
        srcs: Set<String>,
        onImage: (String, ByteArray) -> Unit
    ) {
        val scanner = Fb2Scanner(stream)
        val remaining = srcs.toMutableSet()

        while (remaining.isNotEmpty()) {
            if (!scanner.skipTo(BINARY_OPEN)) return

            // Guard against elements that merely start with the same characters:
            // a real <binary> is followed by an attribute or closes right away.
            val attributes = when (val next = scanner.read()) {
                -1 -> return
                TAG_END -> ""
                in WHITESPACE -> scanner.readUntil(TAG_END) ?: return
                else -> continue
            }

            // A self-closing <binary/> holds no image; scanning on for a closing
            // tag would run past it and swallow the next one.
            if (attributes.trimEnd().endsWith('/')) continue

            val id = BINARY_ID_REGEX.find(attributes)
                ?.let { match -> match.groupValues[2].ifEmpty { match.groupValues[3] } }
                ?.trim()
                ?.lowercase()

            if (id == null || id !in remaining) {
                if (!scanner.skipTo(BINARY_CLOSE)) return
                continue
            }

            val encoded = scanner.collectUntil(BINARY_CLOSE) ?: return
            remaining.remove(id)
            try {
                onImage(id, Base64.decode(encoded, Base64.DEFAULT))
            } catch (e: Exception) {
                logE(TAG, "Could not decode <binary> [$id]: ${e.message}")
            }
        }
    }

    /**
     * Byte-level reader over the book stream. FB2 markup and base64 are ASCII, so
     * scanning bytes needs no charset handling; only attribute text is decoded
     * (as UTF-8) because an id could carry non-ASCII characters.
     */
    private class Fb2Scanner(private val input: InputStream) {

        private val buffer = ByteArray(SCAN_BUFFER)
        private var length = 0
        private var position = 0

        fun read(): Int {
            if (position >= length) {
                length = input.read(buffer)
                position = 0
                if (length <= 0) return -1
            }
            return buffer[position++].toInt() and 0xFF
        }

        /** Consumes up to and including [needle]; false when the stream ends first. */
        fun skipTo(needle: ByteArray): Boolean {
            var matched = 0
            while (true) {
                val byte = read()
                if (byte == -1) return false
                matched = when {
                    byte == needle[matched].toInt() -> matched + 1
                    byte == needle[0].toInt() -> 1
                    else -> 0
                }
                if (matched == needle.size) return true
            }
        }

        /** Text up to (not including) [terminator]; null when the stream ends first. */
        fun readUntil(terminator: Int): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = read()
                if (byte == -1) return null
                if (byte == terminator) return bytes.toString(Charsets.UTF_8.name())
                bytes.write(byte)
            }
        }

        /**
         * Base64 payload up to (not including) [needle], with whitespace dropped;
         * null when the stream ends first.
         */
        fun collectUntil(needle: ByteArray): ByteArray? {
            val bytes = ByteArrayOutputStream()
            var matched = 0
            while (true) {
                val byte = read()
                if (byte == -1) return null
                matched = when {
                    byte == needle[matched].toInt() -> matched + 1
                    byte == needle[0].toInt() -> 1
                    else -> 0
                }
                if (matched == needle.size) {
                    // The tail of the payload is the partially matched needle.
                    val payload = bytes.toByteArray()
                    return payload.copyOf(payload.size - (needle.size - 1))
                }
                if (byte !in WHITESPACE) bytes.write(byte)
            }
        }
    }

    private companion object {
        val BINARY_OPEN = "<binary".toByteArray(Charsets.US_ASCII)
        val BINARY_CLOSE = "</binary".toByteArray(Charsets.US_ASCII)
        const val TAG_END = '>'.code
        val WHITESPACE = setOf(' '.code, '\t'.code, '\n'.code, '\r'.code)
    }
}
