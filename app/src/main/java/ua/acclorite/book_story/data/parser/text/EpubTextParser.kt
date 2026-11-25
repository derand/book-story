/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package ua.acclorite.book_story.data.parser.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.helpers.addAll
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.epub.EpubPackage
import ua.acclorite.book_story.data.parser.epub.readToc
import ua.acclorite.book_story.data.parser.epub.tocKey
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "EpubTextParser"

private val dispatcher = Dispatchers.IO.limitedParallelism(3)

class EpubTextParser @Inject constructor(
    private val documentParser: DocumentParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean): ParsedText {
        logI(TAG, "Started EPUB parsing: ${cachedFile.size} bytes.")

        return try {
            yield()
            var readerText = listOf<ReaderText>()

            val rawFile = cachedFile.rawFile
            if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return ParsedText.EMPTY

            ZipFile(rawFile).use { zip ->
                val epub = EpubPackage.open(zip)
                val chapterTitles = epub.readToc()

                logI(TAG, "OPF Entry: ${epub.opfPath ?: "no .opf entry"}")
                logI(TAG, "Chapter entries, size: ${epub.spine.size}")
                logI(TAG, "Title entries, size: ${chapterTitles?.size}")

                readerText = epub.parseEpub(
                    chapterTitles = chapterTitles,
                    keepImageBytes = keepImageBytes
                )
            }

            yield()

            if (
                readerText.isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                logE(TAG, "Could not extract text from EPUB.")
                return ParsedText.EMPTY
            }

            logI(TAG, "Successfully finished EPUB parsing.")
            ParsedText(readerText)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse text with message: ${e.messageForLog()}.")
            ParsedText.EMPTY
        }
    }

    /**
     * Parses text and chapters from EPUB, the spine's documents in parallel.
     * Titles come from the TOC ([readToc]), else from a document's first line.
     *
     * @param chapterTitles Titles from the TOC, by [tocKey].
     */
    private suspend fun EpubPackage.parseEpub(
        chapterTitles: Map<String, ReaderText.Chapter>?,
        keepImageBytes: Boolean
    ): List<ReaderText> {

        val readerText = mutableListOf<ReaderText>()
        coroutineScope {
            val unformattedText = ConcurrentLinkedQueue<Pair<Int, List<ReaderText>>>()

            // Asynchronously getting all chapters with text
            spine.mapIndexed { index, entry ->
                async(dispatcher) {
                    yield()

                    unformattedText.parseZipEntry(
                        epub = this@parseEpub,
                        index = index,
                        entry = entry,
                        chapterTitles = chapterTitles,
                        keepImageBytes = keepImageBytes
                    )

                    yield()
                }
            }.awaitAll()

            // Sorting chapters in correct order
            readerText.addAll {
                unformattedText.toList()
                    .sortedBy { (index, _) -> index }
                    .map { it.second }
                    .flatten()
            }
        }

        return readerText
    }

    /**
     * Parses [entry] to get it's text and chapter.
     * Adds parsed entry in [ConcurrentLinkedQueue].
     *
     * @param index Index of the [entry] in the spine.
     * @param chapterTitles Titles from [readToc].
     */
    private suspend fun ConcurrentLinkedQueue<Pair<Int, List<ReaderText>>>.parseZipEntry(
        epub: EpubPackage,
        index: Int,
        entry: ZipEntry,
        chapterTitles: Map<String, ReaderText.Chapter>?,
        keepImageBytes: Boolean
    ) {
        // Getting all text
        val content = epub.read(entry)
        // An EPUB content document is XHTML, and read as HTML it is not the same
        // document: HTML has no self-closing <title/> or <a id="…"/>, and a
        // <title/> left open took the text after it into the page title. jsoup's
        // XML parser is lenient where real books are not well-formed (an
        // undeclared &nbsp;, an unclosed tag), so it needs no HTML fallback.
        val document = Jsoup.parse(content, "", Parser.xmlParser()).apply {
            // HTML keeps what these hold out of the text; XML makes it text
            select("script, style").remove()
        }
        var readerText = documentParser.parseDocument(
            document = document,
            images = epub.imagesOf(entry),
            includeChapter = false,
            keepImageBytes = keepImageBytes
        ).toMutableList()

        // Adding chapter title from TOC if found
        chapterTitles?.get(tocKey(entry.name)).apply {
            val chapter = this ?: run {
                val firstVisibleText = readerText.firstOrNull { line ->
                    line is ReaderText.Text && line.line.text.containsVisibleText()
                } as? ReaderText.Text ?: return

                return@run ReaderText.Chapter(
                    title = firstVisibleText.line.text,
                    depth = 0
                )
            }

            readerText = readerText.dropWhile { line ->
                (line is ReaderText.Text && line.line.text.lowercase() == chapter.title.lowercase())
            }.toMutableList()

            readerText.add(
                0,
                chapter
            )
        }

        // A chapter that yielded nothing beyond its own title is empty; one
        // holding only an image (a cover page) is not.
        if (
            readerText.none { line -> line !is ReaderText.Chapter } ||
            readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
        ) {
            logW(TAG, "Could not extract text from [${entry.name}].")
            return
        }

        add(index to readerText)
    }
}
