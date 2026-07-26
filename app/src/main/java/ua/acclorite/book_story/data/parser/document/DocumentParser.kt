/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ua.acclorite.book_story.core.helpers.clearAllMarkdown
import ua.acclorite.book_story.core.helpers.clearMarkdown
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.domain.model.reader.NOTE_LINK_TAG_PREFIX
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
 * Private-use sentinel wrapping FB2 <emphasis>/<em> content. [MarkdownParser]
 * turns the enclosed text into an italic span. Unlike a markdown `_`, the mark
 * also styles intra-word emphasis: CommonMark disables `_` emphasis inside a
 * word, so a single stressed letter \u2014 \u00AB\u0431_\u043E_\u043B\u044C\u0448\u0438\u043D\u0441\u0442\u0432\u043E\u00BB \u2014 would otherwise be
 * dropped (clearMarkdown() strips the underscores, leaving plain text).
 */
const val ITALIC_MARK = "\uE018"

/**
 * Private-use sentinel wrapping a flattened <title> (of an FB2 <poem>/
 * <epigraph>/<cite>). A mark rather than "**", because the title keeps its own
 * inline markup, which may itself carry "**" from a <strong>: nested asterisks
 * would fuse into one malformed emphasis run.
 */
const val BOLD_MARK = "\uE019"

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

/** A GFM table delimiter row, e.g. "| --- | :--: |". */
private val TABLE_DELIMITER_REGEX =
    Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$""")

// Compiled once — all of these run in the per-line hot loop over the whole book
// (parseDocument), so per-line Regex() compilation added up to a large share of
// parse time. Kept at file scope like TABLE_DELIMITER_REGEX above.
private val BOLD_ITALIC_NORMALIZE_REGEX = Regex("""\*\*\*\s*(.*?)\s*\*\*\*""")
private val BOLD_NORMALIZE_REGEX = Regex("""\*\*\s*(.*?)\s*\*\*""")
private val ITALIC_NORMALIZE_REGEX = Regex("""_\s*(.*?)\s*_""")
private val IMAGE_LINE_REGEX = Regex("""\[\[(.*?)\|(.*?)]]""")
private val CHAPTER_LINE_REGEX = Regex("""\[\[\[chapter\|(\d+)\|(.*)]]]""")
private val TABLE_LINE_REGEX = Regex("""\[\[\[table\|(\d+)]]]""")
/** Separator-like text: "---", "***", "___", also spaced out ("* * *"). */
private val SEPARATOR_TEXT_REGEX = Regex("""^([-*_])(\s*\1){2,}$""")
private val NEWLINES_REGEX = Regex("\\n+")
private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * Leading block marker of a line: an ordered/bullet list item, a quote or an
 * ATX heading. See [escapeLeadingBlockMarker].
 */
private val LEADING_BLOCK_MARKER_REGEX = Regex("""^(\s*)(\d{1,9}[.)]|[-+*>]|#{1,6})(\s|$)""")

/**
 * Escapes a leading block marker so commonmark keeps it as text. A chapter
 * title is a title, not markdown: "1. Prologue" must stay "1. Prologue"
 * instead of becoming a list item that swallows its own numbering.
 */
private fun String.escapeLeadingBlockMarker(): String =
    replace(LEADING_BLOCK_MARKER_REGEX) { match ->
        val marker = match.groupValues[2]
        val escaped = when {
            // Only the punctuation of "12." / "12)" needs escaping
            marker.first().isDigit() -> marker.dropLast(1) + "\\" + marker.last()
            else -> "\\" + marker
        }
        "${match.groupValues[1]}$escaped${match.groupValues[3]}"
    }

/**
 * Plain text of a parsed chapter title, for the chapter list and the toolbar:
 * the styling is already resolved, and footnote markers are dropped, as a
 * dangling "[1]" is noise outside the text itself.
 */
private fun AnnotatedString.plainTitle(): String {
    val notes = getLinkAnnotations(0, length).filter { range ->
        val item = range.item
        item is LinkAnnotation.Clickable && item.tag.startsWith(NOTE_LINK_TAG_PREFIX)
    }
    if (notes.isEmpty()) return text.trim()

    return buildString {
        append(text)
        notes.sortedByDescending { note -> note.start }.forEach { note ->
            delete(note.start, note.end)
        }
    }.trim()
}

/** Whether the string carries any styling worth keeping over its plain text. */
private fun AnnotatedString.hasInlineMarkup(): Boolean =
    spanStyles.isNotEmpty() || hasLinkAnnotations(0, length)

/**
 * Flattens a <title> subtree into inline content on a single line, keeping its
 * markup. Block children (<p>, <v>, ...) are unwrapped with a separating
 * space, children that cannot live inside a line (<image>, <empty-line>, ...)
 * are dropped, and all whitespace is collapsed, so neither the source
 * indentation nor an appended "\n" can split the title in two. The inline
 * elements (<emphasis>, <strong>, <sub>, <a>, ...) are left in place: the
 * regular transforms then style a title exactly like body text.
 */
fun Element.flattenTitleToInline() {
    select("empty-line, image, img, table, hr").remove()
    select("p, v, subtitle, stanza, poem").forEach { block ->
        block.after(TextNode(" "))
        block.unwrap()
    }
    collapseWhitespaceDeep()
}

/**
 * Collapses whitespace over the whole subtree, as one continuous run of text:
 * a space split across two text nodes (the source indentation next to the
 * separator of an unwrapped block) collapses into a single one, and the
 * leading whitespace is dropped.
 */
private fun Node.collapseWhitespaceDeep() {
    var lastWasSpace = true

    fun collapse(node: Node) {
        node.childNodes().forEach { child ->
            if (child !is TextNode) {
                collapse(child)
                return@forEach
            }

            val collapsed = StringBuilder(child.wholeText.length)
            child.wholeText.forEach { char ->
                when {
                    !char.isWhitespace() -> {
                        collapsed.append(char)
                        lastWasSpace = false
                    }

                    !lastWasSpace -> {
                        collapsed.append(' ')
                        lastWasSpace = true
                    }
                }
            }
            child.text(collapsed.toString())
        }
    }

    collapse(this)
}

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

        // Tables extracted in the DOM phase, re-emitted by their marker line
        val tables = mutableListOf<ReaderText.Table>()

        document.selectFirst("body")
            .run { this ?: document.body() }
            .apply {
                // Remove manual line breaks from all <p>, <a>. Setting .html()
                // re-parses the fragment, so skip it when there is no newline —
                // most paragraphs in a non-pretty-printed file have none.
                select("p").forEach { element ->
                    yield()
                    val html = element.html()
                    if (html.indexOf('\n') >= 0) {
                        element.html(html.replace(NEWLINES_REGEX, " "))
                    }
                    element.append("\n")
                }
                select("a").forEach { element ->
                    yield()
                    val html = element.html()
                    if (html.indexOf('\n') >= 0) {
                        element.html(html.replace(NEWLINES_REGEX, ""))
                    }
                }

                // Section/body titles are already turned into chapter markers
                // upstream; the titles left here belong to FB2 <poem>/<epigraph>/
                // <cite>. Flatten them into a bold line instead of dropping them,
                // keeping the inline markup for the transforms below.
                select("title").forEach { title ->
                    if (title.wholeText().isBlank()) {
                        title.remove()
                        return@forEach
                    }

                    title.flattenTitleToInline()
                    title.before(TextNode("\n$TITLE_ROLE_MARKER$BOLD_MARK"))
                    title.after(TextNode("$BOLD_MARK\n"))
                    title.unwrap()
                }

                // Markdown
                select("hr").append("\n$SEPARATOR_MARKER\n")
                select("b").append("**").prepend("**")
                select("h1").append("**").prepend("**")
                select("h2").append("**").prepend("**")
                select("h3").append("**").prepend("**")
                select("strong").append("**").prepend("**")
                select("em").prepend(ITALIC_MARK).append(ITALIC_MARK)

                // FB2 inline: <emphasis> is the italic tag (FB2 has no <em>).
                // Wrapped in a sentinel rather than "_": a mark styles intra-word
                // emphasis too, which markdown underscores cannot (see ITALIC_MARK).
                select("emphasis").prepend(ITALIC_MARK).append(ITALIC_MARK)

                // FB2 block-level tags carry no line break of their own, so in
                // files without pretty-printing they glue to surrounding text.
                // A separator-only subtitle ("* * *", "---") is a scene break,
                // not a heading: keep it as literal text on its own line rather
                // than wrapping it in emphasis, which would fuse the marks into
                // the markdown and drop the line entirely. Others get bold+italic.
                select("subtitle").forEach { subtitle ->
                    val text = subtitle.wholeText().trim()
                    if (text.matches(SEPARATOR_TEXT_REGEX)) {
                        subtitle.replaceWith(TextNode("\n$text\n"))
                    } else {
                        subtitle.prepend("\n_**").append("**_\n") // bold + italic
                    }
                }
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

                // Tables: extracted whole (a table cannot be flattened into the
                // line stream), replaced by a marker that re-emits the parsed
                // table. Runs after the inline transforms above, so cell text
                // already carries its markdown. Nested tables are left to the
                // outer one.
                select("table")
                    .filter { table -> table.parents().none { it.tagName() == "table" } }
                    .forEach { table ->
                        val rows = table.select("tr").mapNotNull { row ->
                            val cells = row.select("th, td")
                            if (cells.isEmpty()) return@mapNotNull null
                            cells.map { cell ->
                                markdownParser.parse(
                                    cell.wholeText().replace(WHITESPACE_REGEX, " ").trim()
                                )
                            }
                        }
                        if (rows.isEmpty()) {
                            table.remove()
                            return@forEach
                        }

                        val hasHeader = table.selectFirst("tr")?.selectFirst("th") != null
                        table.replaceWith(TextNode("\n[[[table|${tables.size}]]]\n"))
                        tables.add(ReaderText.Table(rows, hasHeader))
                    }
            }.wholeText().lines()
            .let { lines -> extractMarkdownTables(lines, tables) }
            .forEach { line ->
                yield()

                val formattedLine = line.replace(
                    // Tabs are not rendered and would glue the surrounding words together
                    "\t", " "
                ).replace(
                    BOLD_ITALIC_NORMALIZE_REGEX, "_**$1**_"
                ).replace(
                    BOLD_NORMALIZE_REGEX, "**$1**"
                ).replace(
                    ITALIC_NORMALIZE_REGEX, "_$1_"
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

                val imageRegex = IMAGE_LINE_REGEX
                val chapterRegex = CHAPTER_LINE_REGEX
                val tableRegex = TABLE_LINE_REGEX
                val separatorRegex = SEPARATOR_TEXT_REGEX

                if (line.containsVisibleText()) {
                    val trimmed = line.trim()
                    when {
                        // Poem boundaries: everything in between is collected
                        // into a single poem block
                        trimmed == POEM_BEGIN_MARKER -> poemLines = mutableListOf()
                        trimmed == POEM_END_MARKER -> {
                            poemLines?.takeIf { it.isNotEmpty() }?.let { lines ->
                                readerText.add(ReaderText.Poem(lines.toList()))
                            }
                            poemLines = null
                        }

                        // Empty line marker (from FB2 <empty-line/>). A blank line
                        // cannot survive the containsVisibleText() gate on its own,
                        // so it is carried as a marker and rendered as a blank line.
                        trimmed == EMPTY_LINE_MARKER -> {
                            val blank = ReaderText.Text(AnnotatedString(" "))
                            poemLines?.add(blank) ?: readerText.add(blank)
                        }

                        // Table marker (from <table>)
                        tableRegex.matches(line) -> {
                            val index = tableRegex.matchEntire(line)
                                ?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                            tables.getOrNull(index)?.let { readerText.add(it) }
                        }

                        // Chapter marker (from FB2 <title>), checked before
                        // imageRegex as the latter also matches this line
                        chapterRegex.matches(line) -> {
                            if (!includeChapter) return@forEach

                            val match = chapterRegex.matchEntire(line) ?: return@forEach
                            // The title keeps its inline markup (see
                            // [flattenTitleToInline]), so it is parsed like any
                            // other line; the chapter list gets the plain text.
                            val styledTitle = markdownParser.parse(
                                match.groupValues[2].escapeLeadingBlockMarker()
                            )
                            val title = styledTitle.plainTitle()
                            if (!title.containsVisibleText()) return@forEach

                            readerText.add(
                                ReaderText.Chapter(
                                    title = title,
                                    depth = match.groupValues[1].toIntOrNull() ?: 0,
                                    styledTitle = styledTitle.takeIf { it.hasInlineMarkup() }
                                )
                            )
                            chapterAdded = true
                        }

                        // Section separator (from <hr>)
                        trimmed == SEPARATOR_MARKER -> {
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
     * Renders a footnote body (an FB2 <section> from <body name="notes">) to a
     * formatted [AnnotatedString]: inline styling is kept and paragraphs are
     * separated by a blank line. Runs the same inline markdown transforms as
     * the main flow, on a clone so the source tree is untouched.
     */
    fun parseNote(section: org.jsoup.nodes.Element): AnnotatedString {
        val clone = section.clone()
        clone.select("title").remove()

        clone.select("strong, b").prepend("**").append("**")
        clone.select("emphasis, em").prepend(ITALIC_MARK).append(ITALIC_MARK)
        clone.select("strikethrough").prepend(STRIKETHROUGH_MARK).append(STRIKETHROUGH_MARK)
        clone.select("sub").prepend(SUBSCRIPT_MARK).append(SUBSCRIPT_MARK)
        clone.select("sup").prepend(SUPERSCRIPT_MARK).append(SUPERSCRIPT_MARK)
        clone.select("p").forEach { paragraph ->
            paragraph.html(paragraph.html().replace(NEWLINES_REGEX, " "))
            paragraph.append("\n")
        }

        val paragraphs = clone.wholeText().lines()
            .map { line -> line.trim() }
            .filter { line -> line.containsVisibleText() }

        return buildAnnotatedString {
            paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append("\n\n")
                append(markdownParser.parse(paragraph))
            }
        }
    }

    /**
     * Finds GFM pipe tables in the line stream (a header row, a delimiter row
     * of dashes, then body rows) and replaces each with a table marker,
     * appending the parsed table to [tables]. Unlike HTML/FB2 tables these
     * have no DOM element — markdown tables reach here as plain text lines.
     */
    private fun extractMarkdownTables(
        lines: List<String>,
        tables: MutableList<ReaderText.Table>
    ): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val header = lines[i]
            val delimiter = lines.getOrNull(i + 1)

            if (
                header.contains('|') &&
                delimiter != null &&
                delimiter.contains('-') &&
                TABLE_DELIMITER_REGEX.matches(delimiter)
            ) {
                val rowLines = mutableListOf(header)
                var j = i + 2
                while (j < lines.size && lines[j].contains('|') && lines[j].isNotBlank()) {
                    rowLines.add(lines[j])
                    j++
                }

                val rows = rowLines.map { row ->
                    splitTableRow(row).map { cell -> markdownParser.parse(cell) }
                }
                result.add("[[[table|${tables.size}]]]")
                tables.add(ReaderText.Table(rows, hasHeader = true))
                i = j
            } else {
                result.add(header)
                i++
            }
        }
        return result
    }

    /** Splits a markdown table row into cells, dropping the outer pipes. */
    private fun splitTableRow(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith("|")) row = row.substring(1)
        if (row.endsWith("|")) row = row.dropLast(1)
        return row.split("|").map { it.trim() }
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
                src = src,
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