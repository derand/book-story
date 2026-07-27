/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.reader.ParsedText
import javax.inject.Inject

private const val TAG = "TextParser"

class TextParserImpl @Inject constructor(
    // Markdown parser (Markdown)
    private val txtTextParser: TxtTextParser,
    private val pdfTextParser: PdfTextParser,

    // Document parser (HTML+Markdown)
    private val epubTextParser: EpubTextParser,
    private val htmlTextParser: HtmlTextParser,
    private val xmlTextParser: XmlTextParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean): ParsedText {
        if (!cachedFile.canAccess()) {
            logE(TAG, "File does not exist or no read access is granted.")
            return ParsedText.EMPTY
        }

        val fileFormat = ".${cachedFile.name.substringAfterLast(".")}".lowercase().trim()
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                ".pdf" -> {
                    pdfTextParser.parse(cachedFile, keepImageBytes)
                }

                ".epub" -> {
                    epubTextParser.parse(cachedFile, keepImageBytes)
                }

                ".txt" -> {
                    txtTextParser.parse(cachedFile, keepImageBytes)
                }

                ".fb2" -> {
                    xmlTextParser.parse(cachedFile, keepImageBytes)
                }

                ".html" -> {
                    htmlTextParser.parse(cachedFile, keepImageBytes)
                }

                ".htm" -> {
                    htmlTextParser.parse(cachedFile, keepImageBytes)
                }

                ".md" -> {
                    htmlTextParser.parse(cachedFile, keepImageBytes)
                }

                else -> {
                    logE(TAG, "Wrong file format, could not find supported extension.")
                    ParsedText.EMPTY
                }
            }
        }
    }
}