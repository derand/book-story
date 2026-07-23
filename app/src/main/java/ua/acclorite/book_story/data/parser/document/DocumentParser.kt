/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import ua.acclorite.book_story.core.helpers.clearAllMarkdown
import ua.acclorite.book_story.core.helpers.clearMarkdown
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

/** Marker standing in for FB2 <empty-line/>, resolved to a blank line. */
const val EMPTY_LINE_MARKER = "[[[emptyline]]]"

/**
 * Private-use sentinel wrapping FB2 <strikethrough> content. [MarkdownParser]
 * turns the enclosed text into a real strike-through span, which — unlike a
 * combining overlay — is font independent.
 */
const val STRIKETHROUGH_MARK = "\uE011"

/**
 * Private-use sentinels wrapping FB2 <sub>/<sup> content. [MarkdownParser]
 * turns the enclosed text into a baseline-shifted smaller span — unlike a
 * Unicode character mapping, this covers letters, not just digits and signs.
 */
const val SUBSCRIPT_MARK = "\uE012"
const val SUPERSCRIPT_MARK = "\uE013"

/**
 * Inline reference marks (from FB2 <a l:href="#id">). The run between a
 * start mark and [REF_END_MARK] is "<hex-encoded id>[REF_SEPARATOR]<display
 * text>"; the id is hex-encoded so markdown transformations cannot corrupt
 * it. [MarkdownParser] turns the run into a tappable link annotation.
 * [NOTE_REF_MARK] starts a footnote reference (<a type="note">),
 * [ANCHOR_REF_MARK] \u2014 a plain internal link.
 */
const val NOTE_REF_MARK = "\uE014"
const val ANCHOR_REF_MARK = "\uE017"
const val REF_SEPARATOR = "\uE015"
const val REF_END_MARK = "\uE016"

/** Hex-encodes an FB2 element id for safe transport through the pipeline. */
fun String.encodeReferenceId(): String =
    toByteArray(Charsets.UTF_8).joinToString("") { byte -> "%02x".format(byte) }

/** Reverses [encodeReferenceId]. */
fun String.decodeReferenceId(): String =
    chunked(2).map { chunk -> chunk.toInt(16).toByte() }
        .toByteArray().toString(Charsets.UTF_8)

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
        includeChapter: Boolean = true
    ): List<ReaderText> {
        yield()

        val readerText = mutableListOf<ReaderText>()
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

                // Section/body titles are already turned into chapter markers
                // upstream; the titles left here belong to FB2 <poem>/<epigraph>/
                // <cite>. Flatten them into a bold line instead of dropping them.
                select("title").forEach { title ->
                    val text = title.wholeText().replace(Regex("\\s+"), " ").trim()
                    if (text.isBlank()) title.remove()
                    else title.replaceWith(TextNode("\n**$text**\n"))
                }

                // Markdown
                select("hr").append("\n---\n")
                select("b").append("**").prepend("**")
                select("h1").append("**").prepend("**")
                select("h2").append("**").prepend("**")
                select("h3").append("**").prepend("**")
                select("strong").append("**").prepend("**")
                select("em").append("_").prepend("_")

                // FB2 inline: <emphasis> is the italic tag (FB2 has no <em>)
                select("emphasis").append("_").prepend("_")

                // FB2 block-level tags carry no line break of their own, so in
                // files without pretty-printing they glue to surrounding text.
                select("subtitle").prepend("\n_**").append("**_\n") // bold + italic
                select("poem").prepend("\n").append("\n")
                select("epigraph").prepend("\n").append("\n")
                // Blank line between stanzas, but not after the last one
                select("stanza").forEach { stanza ->
                    if (stanza.nextElementSibling()?.tagName() == "stanza") {
                        stanza.append("\n$EMPTY_LINE_MARKER\n")
                    } else {
                        stanza.append("\n")
                    }
                }
                select("v").append("\n") // verse line
                select("text-author").prepend("\n_").append("_\n")

                // FB2 <epigraph>/<cite> are conventionally set in italic. The "\n"
                // that the loop above appended to each <p> is its last child, so the
                // closing underscore is inserted just before it, not after.
                select("epigraph > p, cite > p").forEach { paragraph ->
                    paragraph.prepend("_")
                    paragraph.childNode(paragraph.childNodeSize() - 1)
                        .before(TextNode("_"))
                }

                // FB2 inline: <code> as a monospace backtick code span
                select("code").prepend("`").append("`")
                // <strikethrough> wrapped in a sentinel, styled by MarkdownParser
                select("strikethrough").prepend(STRIKETHROUGH_MARK).append(STRIKETHROUGH_MARK)
                // <sub>/<sup> wrapped in sentinels, styled by MarkdownParser
                select("sub").prepend(SUBSCRIPT_MARK).append(SUBSCRIPT_MARK)
                select("sup").prepend(SUPERSCRIPT_MARK).append(SUPERSCRIPT_MARK)
                select("a").forEach { element ->
                    // FB2 links the href through the XLink namespace
                    var link = element.attr("xlink:href")
                        .ifBlank { element.attr("l:href") }
                        .ifBlank { element.attr("href") }
                        .trim()
                    if (element.wholeText().isBlank()) return@forEach

                    when {
                        link.startsWith("http") -> {
                            if (link.startsWith("http://")) {
                                link = link.replaceFirst("http://", "https://")
                            }

                            element.prepend("[")
                            element.append("]($link)")
                        }

                        // Internal reference: a footnote (<a type="note">) or
                        // a plain anchor link
                        link.startsWith("#") -> {
                            val id = link.removePrefix("#").trim().lowercase()
                                .encodeReferenceId()
                            val mark = if (element.attr("type") == "note") {
                                NOTE_REF_MARK
                            } else {
                                ANCHOR_REF_MARK
                            }

                            element.prepend("$mark$id$REF_SEPARATOR")
                            element.append(REF_END_MARK)
                        }
                    }
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
                    } ?: "Image"

                    element.append("\n[[$src|$alt]]\n")
                }

                // Image (<image>)
                select("image").forEach { element ->
                    val src = element.attr("xlink:href")
                        .trim()
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf {
                            it.containsVisibleText() && imageEntries?.any { image ->
                                it == image.name.substringAfterLast(File.separator).lowercase()
                            } == true
                        } ?: return@forEach

                    val alt = "Image"

                    element.append("\n[[$src|$alt]]\n")
                }
            }.wholeText().lines().forEach { line ->
                yield()

                val formattedLine = line.replace(
                    Regex("""\*\*\*\s*(.*?)\s*\*\*\*"""), "_**$1**_"
                ).replace(
                    Regex("""\*\*\s*(.*?)\s*\*\*"""), "**$1**"
                ).replace(
                    Regex("""_\s*(.*?)\s*_"""), "_$1_"
                ).trim()

                val imageRegex = Regex("""\[\[(.*?)\|(.*?)]]""")
                val chapterRegex = Regex("""\[\[\[chapter\|([01])\|(.*)]]]""")

                if (line.containsVisibleText()) {
                    when {
                        // Empty line marker (from FB2 <empty-line/>). A blank line
                        // cannot survive the containsVisibleText() gate on its own,
                        // so it is carried as a marker and rendered as a blank line.
                        line.trim() == EMPTY_LINE_MARKER -> {
                            readerText.add(ReaderText.Text(AnnotatedString(" ")))
                        }

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
                            val alt = "_${trimmedLine.substringAfter("|")}_"

                            val image = try {
                                val imageEntry = imageEntries?.find { image ->
                                    src == image.name.substringAfterLast(File.separator).lowercase()
                                } ?: return@forEach

                                zipFile?.getImage(imageEntry)?.asImageBitmap()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            } ?: return@forEach

                            image.prepareToDraw()
                            readerText.add( // Adding image
                                ReaderText.Image(
                                    imageBitmap = image
                                )
                            )
                            readerText.add( // Adding alternative text (caption) for image
                                ReaderText.Text(
                                    markdownParser.parse(alt)
                                )
                            )
                        }

                        line == "---" || line == "***" -> readerText.add(ReaderText.Separator)

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
            return emptyList()
        }

        return readerText
    }

    /**
     * Getting bitmap from [ZipFile] with compression
     * that depends on the [imageEntry] size.
     */
    private fun ZipFile.getImage(imageEntry: ZipEntry): Bitmap? {
        fun getBitmapFromInputStream(compressionLevel: Int = 1): Bitmap? {
            return getInputStream(imageEntry).use { inputStream ->
                BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = compressionLevel
                    }
                )
            }
        }


        val uncompressedBitmap = getBitmapFromInputStream() ?: return null
        return uncompressedBitmap
    }
}