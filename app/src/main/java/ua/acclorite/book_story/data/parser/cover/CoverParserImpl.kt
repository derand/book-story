/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.cover

import ua.acclorite.book_story.core.data.ExtensionsData
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import javax.inject.Inject

private const val TAG = "CoverParser"

class CoverParserImpl @Inject constructor(
    private val epubCoverParser: EpubCoverParser,
    private val fb2CoverParser: Fb2CoverParser
) : CoverParser {

    override suspend fun parse(cachedFile: CachedFile): CoverImage? {
        if (!cachedFile.canAccess()) {
            logE(TAG, "File does not exist or no read access is granted.")
            return null
        }

        val fileFormat = ExtensionsData.formatOf(cachedFile.name)
        return when (fileFormat) {
            ".epub" -> epubCoverParser.parse(cachedFile)
            ".fb2" -> fb2CoverParser.parse(cachedFile)
            null -> {
                logE(TAG, "Wrong file format, could not find supported extension.")
                null
            }
            // PDF, TXT, HTML and Markdown carry no cover
            else -> null
        }
    }
}