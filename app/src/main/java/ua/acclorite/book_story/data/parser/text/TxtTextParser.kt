/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import kotlinx.coroutines.yield
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import javax.inject.Inject

private const val TAG = "TxtTextParser"

class TxtTextParser @Inject constructor(
    private val markdownParser: MarkdownParser
) : TextParser {

    @Suppress("UNUSED_PARAMETER")
    override suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean): ParsedText {
        logI(TAG, "Started TXT parsing: ${cachedFile.size} bytes.")

        return try {
            val readerText = cachedFile.openInputStream()?.bufferedReader()?.useLines { lines ->
                markdownParser.parsePlainLines(lines)
            } ?: return ParsedText.EMPTY

            yield()

            if (
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                logE(TAG, "Could not extract text from TXT.")
                return ParsedText.EMPTY
            }

            logI(TAG, "Successfully finished TXT parsing.")
            ParsedText(readerText)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse text with message: ${e.messageForLog()}.")
            ParsedText.EMPTY
        }
    }
}