/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.EMPTY_LINE_MARKER
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import javax.inject.Inject

private const val TAG = "XmlTextParser"

class XmlTextParser @Inject constructor(
    private val documentParser: DocumentParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile): ParsedText {
        logI(TAG, "Started XML parsing: ${cachedFile.name}.")

        return try {
            val notes = mutableMapOf<String, String>()
            val readerText = cachedFile.openInputStream()?.use { stream ->
                val document = Jsoup.parse(stream, null, "", Parser.xmlParser())

                // FB2 keeps chapter headings in <title> of <body>/<section>.
                // Convert them to chapter markers before [DocumentParser]
                // removes all <title> elements.
                document.selectFirst("body")?.select("title")?.forEach { title ->
                    val parentTag = title.parent()?.tagName()
                    if (parentTag != "body" && parentTag != "section") return@forEach

                    val text = title.wholeText()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (text.isBlank()) return@forEach

                    val nested = title.parents().count { parent ->
                        parent.tagName() == "section"
                    } > 1
                    title.replaceWith(
                        TextNode("\n[[[chapter|${if (nested) 1 else 0}|$text]]]\n")
                    )
                }

                // FB2 <empty-line/> is a blank paragraph. It carries no text, so
                // it is turned into a marker that survives text extraction and is
                // resolved back to a blank line by [DocumentParser].
                document.select("empty-line").forEach { emptyLine ->
                    emptyLine.replaceWith(TextNode("\n$EMPTY_LINE_MARKER\n"))
                }

                // FB2 stores footnote texts in extra bodies (<body name="notes">
                // etc.). Collect them for the in-text note popups; the bodies
                // themselves are not rendered — a note belongs next to the
                // text it explains, not in a separate section at the end.
                document.select("body").drop(1).forEach { extraBody ->
                    extraBody.select("section[id]").forEach { section ->
                        val id = section.attr("id").trim().lowercase()
                        if (id.isBlank()) return@forEach

                        val title = section.selectFirst("title")?.wholeText()
                            ?.replace(Regex("\\s+"), " ")?.trim()
                            ?.takeIf { it.isNotBlank() }
                        val text = section.clone()
                            .apply { select("title").remove() }
                            .wholeText().replace(Regex("\\s+"), " ").trim()
                        if (text.isBlank()) return@forEach

                        notes[id] = listOfNotNull(
                            title?.let { "$it." },
                            text
                        ).joinToString(" ")
                    }
                }

                documentParser.parseDocument(document)
            }

            yield()

            if (
                readerText.isNullOrEmpty() ||
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                logE(TAG, "Could not extract text from XML.")
                return ParsedText.EMPTY
            }

            logI(TAG, "Successfully finished XML parsing.")
            ParsedText(readerText, notes)
        } catch (e: Exception) {
            logE(TAG, "Could not parse text with message: ${e.message}.")
            ParsedText.EMPTY
        }
    }
}