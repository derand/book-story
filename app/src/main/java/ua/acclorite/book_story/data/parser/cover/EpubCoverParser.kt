/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.cover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.epub.EpubPackage
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "EpubCoverParser"

class EpubCoverParser @Inject constructor() : CoverParser {

    override suspend fun parse(cachedFile: CachedFile): CoverImage? {
        return try {
            var coverImage: CoverImage? = null

            val rawFile = cachedFile.rawFile
            if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return null

            withContext(Dispatchers.IO) {
                ZipFile(rawFile).use { zip ->
                    val epub = EpubPackage.open(zip)
                    val cover = epub.coverImage ?: return@use
                    coverImage = epub.imageBytes(cover.name)?.let { decodeCover(it) }
                }
            }
            coverImage
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse cover image with message: ${e.messageForLog()}.")
            null
        }
    }
}