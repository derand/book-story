/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import ua.acclorite.book_story.core.helpers.clearAllMarkdown
import ua.acclorite.book_story.core.helpers.clearMarkdown
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

class DocumentParser @Inject constructor(
    private val markdownParser: MarkdownParser
) {
    /**
     * Parses document to get it's text.
     * Fixes issues such as manual line breaking in <p>.
     * Applies Markdown to the text: Bold(**), Italic(_), Section separator(---), and Links(a > href).
     *
     * @return Parsed text line by line with Markdown(all lines are not blank).
     */
    suspend fun parseDocument(
        document: Document,
        zipFile: ZipFile? = null,
        imageEntries: List<ZipEntry>? = null,
        base64Images: Map<String, String>? = null,
        includeChapter: Boolean = true
    ): List<ReaderText> = coroutineScope {
        yield()

        val readerText = mutableListOf<ReaderText>()
        // Images decode in parallel while the text is being parsed
        val imageJobs = mutableMapOf<String, Deferred<ReaderImage?>>()
        var chapterAdded = false

        document.selectFirst("body")
            .run { this ?: document.body() }
            .apply {
                // Remove manual line breaks from all <p>, <a>
                select("p").forEach { element ->
                    yield()
                    element.html(element.html().replace(Regex("\\n+"), " "))
                    element.append("\n")
                }
                select("a").forEach { element ->
                    yield()
                    element.html(element.html().replace(Regex("\\n+"), ""))
                }

                // Remove <head>'s title
                select("title").remove()

                // Markdown
                select("hr").append("\n---\n")
                select("b").append("**").prepend("**")
                select("h1").append("**").prepend("**")
                select("h2").append("**").prepend("**")
                select("h3").append("**").prepend("**")
                select("strong").append("**").prepend("**")
                select("em").append("_").prepend("_")
                select("a").forEach { element ->
                    var link = element.attr("href")
                    if (!link.startsWith("http") || element.wholeText().isBlank()) return@forEach

                    if (link.startsWith("http://")) {
                        link = link.replaceFirst("http://", "https://")
                    }

                    element.prepend("[")
                    element.append("]($link)")
                }

                // Image (<img>)
                select("img").forEach { element ->
                    val src = element.attr("src")
                        .trim()
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf {
                            it.containsVisibleText() && imageEntries?.any { image ->
                                it == image.name.substringAfterLast(File.separator).lowercase()
                            } == true
                        } ?: return@forEach

                    val alt = element.attr("alt").trim().takeIf {
                        it.clearMarkdown().containsVisibleText()
                    } ?: ""

                    imageJobs.getOrPut(src) {
                        async(Dispatchers.Default) {
                            loadImage(src, zipFile, imageEntries, base64Images)
                        }
                    }
                    element.append("\n[[$src|$alt]]\n")
                }

                // Image (<image>, FB2 references <binary> by id)
                select("image").forEach { element ->
                    val src = element.attr("xlink:href")
                        .ifBlank { element.attr("l:href") }
                        .ifBlank { element.attr("href") }
                        .trim()
                        .removePrefix("#")
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf {
                            it.containsVisibleText() && (
                                    base64Images?.containsKey(it) == true ||
                                            imageEntries?.any { image ->
                                                it == image.name.substringAfterLast(File.separator).lowercase()
                                            } == true
                                    )
                        } ?: return@forEach

                    imageJobs.getOrPut(src) {
                        async(Dispatchers.Default) {
                            loadImage(src, zipFile, imageEntries, base64Images)
                        }
                    }
                    element.append("\n[[$src|]]\n")
                }
            }.wholeText().lines().forEach { line ->
                yield()

                val formattedLine = line.replace(
                    // Tabs are not rendered and would glue the surrounding words together
                    "\t", " "
                ).replace(
                    Regex("""\*\*\*\s*(.*?)\s*\*\*\*"""), "_**$1**_"
                ).replace(
                    Regex("""\*\*\s*(.*?)\s*\*\*"""), "**$1**"
                ).replace(
                    Regex("""_\s*(.*?)\s*_"""), "_$1_"
                ).trim()

                val imageRegex = Regex("""\[\[(.*?)\|(.*?)]]""")
                val chapterRegex = Regex("""\[\[\[chapter\|([01])\|(.*)]]]""")
                // Section separator: "---", "***", "___", also spaced out ("* * *")
                val separatorRegex = Regex("""^([-*_])(\s*\1){2,}$""")

                if (line.containsVisibleText()) {
                    when {
                        // Chapter marker (from FB2 <title>), checked before
                        // imageRegex as the latter also matches this line
                        chapterRegex.matches(line) -> {
                            if (!includeChapter) return@forEach

                            val match = chapterRegex.matchEntire(line) ?: return@forEach
                            val title = match.groupValues[2].clearAllMarkdown().trim()
                            if (!title.containsVisibleText()) return@forEach

                            readerText.add(
                                ReaderText.Chapter(
                                    title = title,
                                    nested = match.groupValues[1] == "1"
                                )
                            )
                            chapterAdded = true
                        }

                        imageRegex.matches(line) -> {
                            val trimmedLine = line.removeSurrounding("[[", "]]")
                            val src = trimmedLine.substringBefore("|")
                            val alt = trimmedLine.substringAfter("|")

                            val image = imageJobs[src]?.await() ?: return@forEach

                            readerText.add(
                                ReaderText.Image(
                                    image = image,
                                    caption = alt.takeIf { caption ->
                                        caption.clearMarkdown().containsVisibleText()
                                    }?.let { caption -> // Alternative text (caption) for image
                                        ReaderText.Text(markdownParser.parse("_${caption}_"))
                                    }
                                )
                            )
                        }

                        separatorRegex.matches(formattedLine) -> {
                            readerText.add(ReaderText.Separator)
                        }

                        else -> {
                            if (
                                !chapterAdded &&
                                formattedLine.clearAllMarkdown().containsVisibleText() &&
                                includeChapter
                            ) {
                                readerText.add(
                                    0, ReaderText.Chapter(
                                        title = formattedLine.clearAllMarkdown(),
                                        nested = false
                                    )
                                )
                                chapterAdded = true
                            } else if (
                                formattedLine.clearMarkdown().containsVisibleText()
                            ) {
                                readerText.add(
                                    ReaderText.Text(
                                        line = markdownParser.parse(formattedLine)
                                    )
                                )
                            }
                        }
                    }
                }
            }

        yield()

        if (
            readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
            (includeChapter && readerText.filterIsInstance<ReaderText.Chapter>().isEmpty())
        ) {
            return@coroutineScope emptyList()
        }

        readerText
    }

    /**
     * Loading the encoded image bytes from a [ZipFile] entry (EPUB)
     * or a Base64 <binary> (FB2). Only the image bounds are decoded here,
     * pixels are decoded lazily when the reader shows the image.
     *
     * @return Null if the image was not found or is not decodable.
     */
    private fun loadImage(
        src: String,
        zipFile: ZipFile?,
        imageEntries: List<ZipEntry>?,
        base64Images: Map<String, String>?
    ): ReaderImage? {
        return try {
            val bytes = base64Images?.get(src)?.let { encoded ->
                Base64.decode(encoded, Base64.DEFAULT)
            } ?: imageEntries?.find { image ->
                src == image.name.substringAfterLast(File.separator).lowercase()
            }?.let { imageEntry ->
                zipFile?.getInputStream(imageEntry)?.use { it.readBytes() }
            } ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val checksum = CRC32().apply { update(bytes) }.value
            ReaderImage(
                id = "$src-${bytes.size}-$checksum",
                bytes = bytes,
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}