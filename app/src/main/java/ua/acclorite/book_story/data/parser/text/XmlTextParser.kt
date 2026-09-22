/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.document.DocumentParser
import androidx.compose.ui.text.AnnotatedString
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import javax.inject.Inject

private const val TAG = "XmlTextParser"

class XmlTextParser @Inject constructor(
    private val documentParser: DocumentParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean): ParsedText {
        logI(TAG, "Started XML parsing: ${cachedFile.size} bytes.")

        return try {
            val notes = mutableMapOf<String, AnnotatedString>()
            val readerText = cachedFile.openInputStream()?.use { stream ->
                val document = timed("    jsoup") {
                    Jsoup.parse(stream, null, "", Parser.xmlParser())
                }

                // FB2 stores images as Base64 <binary id="...">, referenced by <image>
                val base64Images = document.select("binary").associate { binary ->
                    binary.attr("id").trim().lowercase() to binary.wholeText()
                }.filterKeys { it.isNotBlank() }
                document.select("binary").remove()

                // FB2 stores footnote texts in extra bodies (<body name="notes">
                // etc.). Collect them for the in-text note popups; the bodies
                // themselves are not rendered — a note belongs next to the
                // text it explains, not in a separate section at the end.
                document.select("body").drop(1).forEach { extraBody ->
                    extraBody.select("section[id]").forEach { section ->
                        val id = section.attr("id").trim().lowercase()
                        if (id.isBlank()) return@forEach

                        val note = documentParser.parseNote(section)
                        if (note.isBlank()) return@forEach
                        notes[id] = note
                    }
                }

                timed("    parseDoc") {
                    documentParser.parseDocument(
                        document = document,
                        base64Images = base64Images,
                        sectionTitles = true,
                        keepImageBytes = keepImageBytes
                    )
                }
            }

            yield()

            if (
                readerText.isNullOrEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                logE(TAG, "Could not extract text from XML.")
                return ParsedText.EMPTY
            }

            logI(TAG, "Successfully finished XML parsing.")

            ParsedText(readerText, notes)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse text with message: ${e.messageForLog()}.")
            ParsedText.EMPTY
        }
    }
}