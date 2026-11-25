/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.document.MarkdownLine
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.document.SEPARATOR_TEXT_REGEX
import ua.acclorite.book_story.data.parser.document.YIELD_INTERVAL
import ua.acclorite.book_story.data.parser.document.splitMarkdownBlocks
import ua.acclorite.book_story.data.parser.document.withoutBlankEdges
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val TAG = "MarkdownTextParser"

/**
 * A `.md` file: markdown, one paragraph per line.
 *
 * It used to go through [HtmlTextParser] — jsoup read the markdown as the text
 * of an HTML body, and the document parser flattened it back into the lines it
 * started as. That only worked because the document parser itself ran
 * commonmark over every line; once it walks the DOM instead, markdown needs a
 * path that actually reads markdown. The rules are the ones that path applied:
 * the first visible line names the chapter, pipe tables become tables, a line
 * of separator characters stays as literal text. A fenced code block stays one
 * block, because a line-by-line reading cannot see it.
 */
class MarkdownTextParser @Inject constructor(
    private val markdownParser: MarkdownParser
) : TextParser {

    @Suppress("UNUSED_PARAMETER")
    override suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean): ParsedText {
        logI(TAG, "Started Markdown parsing: ${cachedFile.size} bytes.")

        return try {
            val lines = cachedFile.openInputStream()?.bufferedReader()?.use { reader ->
                reader.readText().removePrefix("\uFEFF").lines()
            } ?: return ParsedText.EMPTY

            val readerText = mutableListOf<ReaderText>()
            var chapterAdded = false

            splitMarkdownBlocks(lines, markdownParser).forEachIndexed { index, markdownLine ->
                coroutineContext.ensureActive()
                if (index % YIELD_INTERVAL == 0) yield()

                val line = when (markdownLine) {
                    is MarkdownLine.Table -> {
                        readerText.add(markdownLine.table)
                        return@forEachIndexed
                    }

                    is MarkdownLine.Code -> {
                        var code = markdownLine.lines.map { AnnotatedString(it) }
                            .withoutBlankEdges()

                        // A file needs a chapter, even if it opens with code
                        if (!chapterAdded && code.isNotEmpty()) {
                            readerText.add(0, ReaderText.Chapter(title = code.first().text.trim()))
                            chapterAdded = true
                            code = code.drop(1).withoutBlankEdges()
                        }

                        if (code.isNotEmpty()) readerText.add(ReaderText.Preformatted(code))
                        return@forEachIndexed
                    }

                    is MarkdownLine.Text -> markdownLine.line.replace("\t", " ").trim()
                }
                if (!line.containsVisibleText()) return@forEachIndexed

                when {
                    // Without this, commonmark would take "* * *" for a thematic
                    // break and drop the line: it is the author's scene break
                    SEPARATOR_TEXT_REGEX.matches(line) -> {
                        readerText.add(ReaderText.Text(AnnotatedString(line)))
                    }

                    // "# My Book" names the chapter "My Book"
                    !chapterAdded -> {
                        val title = markdownParser.parse(line).text
                        readerText.add(0, ReaderText.Chapter(title = title))
                        chapterAdded = true
                    }

                    else -> {
                        readerText.add(ReaderText.Text(markdownParser.parse(line)))
                    }
                }
            }

            yield()

            if (readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()) {
                logE(TAG, "Could not extract text from Markdown.")
                return ParsedText.EMPTY
            }

            logI(TAG, "Successfully finished Markdown parsing.")
            ParsedText(readerText)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse text with message: ${e.messageForLog()}.")
            ParsedText.EMPTY
        }
    }
}
