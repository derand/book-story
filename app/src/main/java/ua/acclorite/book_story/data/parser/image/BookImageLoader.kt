/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.image

import android.util.Base64
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookImageLoader"

/**
 * Re-loads the encoded bytes of book images by their `src` lookup key, straight
 * from the source file — without re-parsing the text. Used to refill images on a
 * parse-cache hit (the cache stores image metadata only). The `src` matching
 * mirrors [ua.acclorite.book_story.data.parser.document.DocumentParser]:
 * FB2 keys are the lowercased `<binary>` id, EPUB keys the lowercased basename.
 *
 * Only FB2 and EPUB carry image bytes; every other format returns nothing.
 */
@Singleton
class BookImageLoader @Inject constructor() {

    /** Returns encoded bytes for the [srcs] that could be resolved, keyed by src. */
    fun loadImageBytes(cachedFile: CachedFile, srcs: Set<String>): Map<String, ByteArray> {
        if (srcs.isEmpty()) return emptyMap()
        val format = ".${cachedFile.name.substringAfterLast(".")}".lowercase().trim()
        return when (format) {
            ".fb2" -> loadFromFb2(cachedFile, srcs)
            ".epub" -> loadFromEpub(cachedFile, srcs)
            else -> emptyMap()
        }
    }

    private fun loadFromFb2(cachedFile: CachedFile, srcs: Set<String>): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>(srcs.size)
        try {
            cachedFile.openInputStream()?.use { stream ->
                val document = Jsoup.parse(stream, null, "", Parser.xmlParser())
                document.select("binary").forEach { binary ->
                    val id = binary.attr("id").trim().lowercase()
                    if (id in srcs && id !in result) {
                        try {
                            result[id] = Base64.decode(binary.wholeText(), Base64.DEFAULT)
                        } catch (e: Exception) {
                            logE(TAG, "Could not decode <binary> [$id]: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logE(TAG, "Could not load FB2 images: ${e.message}")
        }
        return result
    }

    private fun loadFromEpub(cachedFile: CachedFile, srcs: Set<String>): Map<String, ByteArray> {
        val rawFile = cachedFile.rawFile
        if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return emptyMap()
        val result = HashMap<String, ByteArray>(srcs.size)
        try {
            ZipFile(rawFile).use { zip ->
                val entries = zip.entries().toList()
                srcs.forEach { src ->
                    val entry = entries.find { entry ->
                        src == entry.name.substringAfterLast(File.separator).lowercase()
                    } ?: return@forEach
                    try {
                        result[src] = zip.getInputStream(entry).use { it.readBytes() }
                    } catch (e: Exception) {
                        logE(TAG, "Could not read EPUB image [$src]: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logE(TAG, "Could not load EPUB images: ${e.message}")
        }
        return result
    }
}
