/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import ua.acclorite.book_story.core.helpers.clearAllMarkdown
import ua.acclorite.book_story.core.helpers.clearMarkdown
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

/** Marker standing in for FB2 <empty-line/>, resolved to a blank line. */
const val EMPTY_LINE_MARKER = "[[[emptyline]]]"

/** Marker standing in for <hr>, resolved to a [ReaderText.Separator]. */
const val SEPARATOR_MARKER = "[[[separator]]]"

/** Markers wrapping an FB2 <poem>; lines in between are poem content. */
const val POEM_BEGIN_MARKER = "[[[poem-begin]]]"
const val POEM_END_MARKER = "[[[poem-end]]]"

/** Line-prefix markers carrying the [ReaderTextRole] of the line. */
const val TITLE_ROLE_MARKER = "[[[role-title]]]"
const val EPIGRAPH_ROLE_MARKER = "[[[role-epigraph]]]"
const val AUTHOR_ROLE_MARKER = "[[[role-author]]]"

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
     * Applies Markdown to the text: Bold(**), Italic(_), Section separator(hr), and Links(a > href).
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

        // Non-null while between poem markers: lines are collected here and
        // flushed as a single [ReaderText.Poem] block
        var poemLines: MutableList<ReaderText.Text>? = null

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
                    else title.replaceWith(TextNode("\n$TITLE_ROLE_MARKER**$text**\n"))
                }

                // Markdown
                select("hr").append("\n$SEPARATOR_MARKER\n")
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
                select("poem").prepend("\n$POEM_BEGIN_MARKER\n").append("\n$POEM_END_MARKER\n")
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
                select("text-author").prepend("\n${AUTHOR_ROLE_MARKER}_").append("_\n")

                // FB2 <epigraph>/<cite> are conventionally set in italic. The "\n"
                // that the loop above appended to each <p> is its last child, so the
                // closing underscore is inserted just before it, not after.
                select("epigraph > p, cite > p").forEach { paragraph ->
                    paragraph.prepend("_")
                    paragraph.childNode(paragraph.childNodeSize() - 1)
                        .before(TextNode("_"))
                }
                // Prepended after the italic wrapping, so the marker ends up
                // first on the line
                select("epigraph > p").forEach { paragraph ->
                    paragraph.prepend(EPIGRAPH_ROLE_MARKER)
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

                // Role marker prefix (from FB2 <title>/<epigraph>/<text-author>)
                val (role, styledLine) = when {
                    formattedLine.startsWith(TITLE_ROLE_MARKER) ->
                        ReaderTextRole.Title to
                                formattedLine.removePrefix(TITLE_ROLE_MARKER)

                    formattedLine.startsWith(EPIGRAPH_ROLE_MARKER) ->
                        ReaderTextRole.Epigraph to
                                formattedLine.removePrefix(EPIGRAPH_ROLE_MARKER)

                    formattedLine.startsWith(AUTHOR_ROLE_MARKER) ->
                        ReaderTextRole.TextAuthor to
                                formattedLine.removePrefix(AUTHOR_ROLE_MARKER)

                    else -> ReaderTextRole.Paragraph to formattedLine
                }

                val imageRegex = Regex("""\[\[(.*?)\|(.*?)]]""")
                val chapterRegex = Regex("""\[\[\[chapter\|(\d+)\|(.*)]]]""")
                // Separator-like text: "---", "***", "___", also spaced out ("* * *")
                val separatorRegex = Regex("""^([-*_])(\s*\1){2,}$""")

                if (line.containsVisibleText()) {
                    when {
                        // Poem boundaries: everything in between is collected
                        // into a single poem block
                        line.trim() == POEM_BEGIN_MARKER -> poemLines = mutableListOf()
                        line.trim() == POEM_END_MARKER -> {
                            poemLines?.takeIf { it.isNotEmpty() }?.let { lines ->
                                readerText.add(ReaderText.Poem(lines.toList()))
                            }
                            poemLines = null
                        }

                        // Empty line marker (from FB2 <empty-line/>). A blank line
                        // cannot survive the containsVisibleText() gate on its own,
                        // so it is carried as a marker and rendered as a blank line.
                        line.trim() == EMPTY_LINE_MARKER -> {
                            val blank = ReaderText.Text(AnnotatedString(" "))
                            poemLines?.add(blank) ?: readerText.add(blank)
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
                                    depth = match.groupValues[1].toIntOrNull() ?: 0
                                )
                            )
                            chapterAdded = true
                        }

                        // Section separator (from <hr>)
                        line.trim() == SEPARATOR_MARKER -> {
                            readerText.add(ReaderText.Separator)
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

                        // A line of separator characters ("* * *", "---") is the
                        // author's literal scene-break text, kept visible as-is.
                        // Without this branch markdownParser.parse() would swallow
                        // it as a thematic break, and the clearMarkdown() gate
                        // below would drop the line entirely.
                        separatorRegex.matches(formattedLine) -> {
                            readerText.add(
                                ReaderText.Text(
                                    AnnotatedString(line.replace("\t", " ").trim())
                                )
                            )
                        }

                        else -> {
                            if (
                                !chapterAdded &&
                                poemLines == null &&
                                styledLine.clearAllMarkdown().containsVisibleText() &&
                                includeChapter
                            ) {
                                readerText.add(
                                    0, ReaderText.Chapter(
                                        title = styledLine.clearAllMarkdown()
                                    )
                                )
                                chapterAdded = true
                            } else if (
                                styledLine.clearMarkdown().containsVisibleText()
                            ) {
                                val text = ReaderText.Text(
                                    line = markdownParser.parse(styledLine),
                                    role = role
                                )
                                poemLines?.add(text) ?: readerText.add(text)
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