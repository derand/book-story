/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import android.app.Application
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.domain.model.reader.ParsedText
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ParseCache"

/**
 * On-disk cache of a parsed book ([ParsedText]) so repeat opens skip the parse.
 *
 * Lives in [Application.getCacheDir] — the OS may purge it under storage
 * pressure, so it is strictly best-effort: a miss simply re-parses. Each book is
 * a directory `parsed_books/<key>` keyed by the source file identity
 * (path + size + last-modified) plus [VERSION]; bumping [VERSION] on any parser
 * or format change invalidates every stale entry. The directory holds the parsed
 * text ([TEXT_FILE]) and, when image caching is enabled, one file per image blob
 * under [IMG_DIR]. The size cap ([enforceCap]) bounds the whole tree — a book is
 * evicted (text + its blobs together) as a unit, least-recently-used first.
 *
 * The parsed text stores images as metadata only (see [ParsedTextCodec]); a
 * restored image carries empty bytes. Its bytes are (re)loaded either from a
 * cached blob ([readImageBlobs]) or from the source book file.
 */
@Singleton
class ParseCache @Inject constructor(application: Application) {

    private val dir = File(application.cacheDir, DIR_NAME)

    /** Returns the cached [ParsedText] for this source, or null on a miss. */
    fun read(path: String, size: Long, lastModified: Long): ParsedText? {
        val entry = entryDir(path, size, lastModified)
        val text = textFile(entry)
        if (!text.exists()) return null
        return try {
            val parsed = DataInputStream(BufferedInputStream(FileInputStream(text))).use {
                ParsedTextCodec.decode(it)
            }
            // Touch on a hit so eviction by oldest-modified is true LRU.
            entry.setLastModified(System.currentTimeMillis())
            parsed
        } catch (e: Exception) {
            // Corrupt/partial/old entry — drop it and treat as a miss.
            logE(TAG, "Could not read cache entry, dropping it: ${e.message}")
            entry.deleteRecursively()
            null
        }
    }

    /**
     * Stores [parsed] (and, if given, its [images] blobs) for this source,
     * overwriting any existing entry, then evicts least-recently-used books so
     * the total stays within [maxBytes] (the entry just written is never
     * evicted). Pass [Long.MAX_VALUE] for no cap.
     */
    fun write(
        path: String,
        size: Long,
        lastModified: Long,
        parsed: ParsedText,
        maxBytes: Long = Long.MAX_VALUE,
        images: Map<String, ByteArray>? = null
    ) {
        val entry = entryDir(path, size, lastModified)
        if (!entry.exists() && !entry.mkdirs()) {
            logE(TAG, "Could not create cache entry dir.")
            return
        }
        val text = textFile(entry)
        // Write to a temp file then rename, so a crash mid-write cannot leave a
        // half-written entry that would later decode into garbage.
        val tmp = File(entry, "$TEXT_FILE.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use {
                ParsedTextCodec.encode(parsed, it)
            }
            if (!tmp.renameTo(text)) {
                tmp.copyTo(text, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            logE(TAG, "Could not write cache entry: ${e.message}")
            tmp.delete()
            return
        }
        images?.forEach { (src, bytes) -> writeBlob(entry, src, bytes) }
        entry.setLastModified(System.currentTimeMillis())
        if (maxBytes != Long.MAX_VALUE) enforceCap(maxBytes, keep = entry)
    }

    /**
     * Returns the cached image blobs for [srcs] that are present on disk, keyed
     * by src. Missing srcs are simply absent from the result.
     */
    fun readImageBlobs(
        path: String,
        size: Long,
        lastModified: Long,
        srcs: Set<String>
    ): Map<String, ByteArray> {
        if (srcs.isEmpty()) return emptyMap()
        val entry = entryDir(path, size, lastModified)
        if (!entry.exists()) return emptyMap()
        val result = HashMap<String, ByteArray>(srcs.size)
        srcs.forEach { src ->
            val blob = blobFile(entry, src)
            if (blob.exists()) {
                try {
                    result[src] = blob.readBytes()
                } catch (e: Exception) {
                    logE(TAG, "Could not read image blob: ${e.message}")
                }
            }
        }
        if (result.isNotEmpty()) entry.setLastModified(System.currentTimeMillis())
        return result
    }

    /**
     * Persists [images] blobs for this source (only if a text entry already
     * exists — blobs never live without their book), then enforces [maxBytes].
     * Used on a cache hit after (re)loading bytes from the source book.
     */
    fun writeImageBlobs(
        path: String,
        size: Long,
        lastModified: Long,
        images: Map<String, ByteArray>,
        maxBytes: Long = Long.MAX_VALUE
    ) {
        if (images.isEmpty()) return
        val entry = entryDir(path, size, lastModified)
        if (!textFile(entry).exists()) return
        images.forEach { (src, bytes) -> writeBlob(entry, src, bytes) }
        entry.setLastModified(System.currentTimeMillis())
        if (maxBytes != Long.MAX_VALUE) enforceCap(maxBytes, keep = entry)
    }

    /**
     * Evicts least-recently-used books until the cache is within [maxBytes].
     * Best-effort; call e.g. when the size-cap setting is lowered.
     */
    fun trimToSize(maxBytes: Long) = enforceCap(maxBytes, keep = null)

    /** Total size in bytes of every cached book (text + image blobs). */
    fun totalSizeBytes(): Long =
        entryDirs().sumOf { dirSize(it) }

    /** Size in bytes of the cached book for this source, or 0 if there is none. */
    fun entrySizeBytes(path: String, size: Long, lastModified: Long): Long =
        dirSize(entryDir(path, size, lastModified))

    /** Removes the cached book for this source, if any. */
    fun remove(path: String, size: Long, lastModified: Long) {
        entryDir(path, size, lastModified).deleteRecursively()
    }

    /** Deletes every cached book. */
    fun clear() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun writeBlob(entry: File, src: String, bytes: ByteArray) {
        val imgDir = File(entry, IMG_DIR)
        if (!imgDir.exists() && !imgDir.mkdirs()) {
            logE(TAG, "Could not create image cache dir.")
            return
        }
        val blob = blobFile(entry, src)
        val tmp = File(imgDir, "${blob.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(blob)) {
                tmp.copyTo(blob, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            logE(TAG, "Could not write image blob: ${e.message}")
            tmp.delete()
        }
    }

    /**
     * Deletes oldest-modified books until the total is within [maxBytes], never
     * touching [keep] (the book a caller just wrote and still needs).
     */
    private fun enforceCap(maxBytes: Long, keep: File?) {
        val entries = entryDirs()
        var total = entries.sumOf { dirSize(it) }
        if (total <= maxBytes) return
        entries.asSequence()
            .filter { it != keep }
            .sortedBy { it.lastModified() } // least-recently-used first
            .forEach { entry ->
                if (total <= maxBytes) return
                val freed = dirSize(entry)
                if (entry.deleteRecursively()) total -= freed
            }
    }

    /** All committed book directories. */
    private fun entryDirs(): List<File> =
        dir.listFiles()?.filter { it.isDirectory } ?: emptyList()

    private fun dirSize(entry: File): Long {
        if (!entry.exists()) return 0
        return entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun entryDir(path: String, size: Long, lastModified: Long): File =
        File(dir, sha256Hex("$VERSION|$path|$size|$lastModified"))

    private fun textFile(entry: File): File = File(entry, TEXT_FILE)

    private fun blobFile(entry: File, src: String): File =
        File(File(entry, IMG_DIR), sha256Hex(src))

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val DIR_NAME = "parsed_books"
        private const val TEXT_FILE = "text"
        private const val IMG_DIR = "img"

        /**
         * Bump on any change to parser output or [ParsedTextCodec] format so
         * stale entries are ignored instead of rendering outdated text.
         */
        // 4: tables carry per-column alignment (ParsedTextCodec format 3).
        const val VERSION = 4
    }
}
