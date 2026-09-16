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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.yield
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.domain.model.reader.NOTE_LINK_TAG_PREFIX
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.TableAlignment
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

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

/*
 * Inline styling travels from the DOM to [MarkdownParser] as private-use
 * sentinels rather than as markdown "**"/"_".
 *
 * The DOM is flattened into one flat string here, so a style has to be encoded
 * into the characters of that string — and markdown's delimiters are ordinary
 * punctuation an author may well have typed. Once ours and theirs are mixed no
 * later pass can tell them apart, which is how "(*)" used to render as "()".
 * The private-use area is reserved by Unicode for exactly this kind of private
 * agreement, so a sentinel cannot collide with the book's own text (whatever
 * the book itself brings in is dropped by [stripInlineMarks] on the way in).
 *
 * Sentinels are also stronger than delimiters: [MarkdownParser] treats each as
 * a toggle, so they compose freely, ignore CommonMark's flanking rules — which
 * is what makes intra-word emphasis work — and need no balancing.
 */

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
 * dropped, leaving plain text.
 */
const val ITALIC_MARK = "\uE018"

/**
 * Private-use sentinel for bold: <b>/<strong>/<h1..h3> and a flattened <title>
 * (of an FB2 <poem>/<epigraph>/<cite>).
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

/**
 * The private-use block every sentinel above lives in. A new mark only has to
 * stay inside this range for [stripInlineMarks] to keep covering it.
 */
private val MARK_RANGE = '\uE011'..'\uE019'

/**
 * Drops the sentinel characters the string itself carries, so nothing in the
 * book can be mistaken for a mark this parser injected. Unicode reserves the
 * private-use area for private agreement — but someone else may have made one:
 * Apple's logo sits at U+F8FF and legacy CJK fonts use the area too.
 */
private fun String.stripInlineMarks(): String =
    if (none { char -> char in MARK_RANGE }) this
    else filterNot { char -> char in MARK_RANGE }

/** Applies [stripInlineMarks] to every text node of the subtree. */
private fun Node.stripInlineMarks() {
    childNodes().forEach { child ->
        if (child !is TextNode) {
            child.stripInlineMarks()
            return@forEach
        }

        val text = child.wholeText
        val stripped = text.stripInlineMarks()
        if (stripped !== text) child.text(stripped)
    }
}

/** Every inline styling sentinel, as a character class. */
private val INLINE_MARKS_REGEX = Regex(
    "[$STRIKETHROUGH_MARK$SUBSCRIPT_MARK$SUPERSCRIPT_MARK$ITALIC_MARK$BOLD_MARK]"
)

/** A whole reference run: "<mark><hex id><separator><display text><end>". */
private val REFERENCE_RUN_REGEX = Regex(
    "[$NOTE_REF_MARK$ANCHOR_REF_MARK][^$REF_SEPARATOR$REF_END_MARK]*" +
            "$REF_SEPARATOR([^$REF_END_MARK]*)$REF_END_MARK"
)

/**
 * Drops the inline sentinels, keeping the text they wrap \u2014 for the places that
 * need plain text instead of a styled [AnnotatedString]. Unlike
 * [ua.acclorite.book_story.core.helpers.clearMarkdown] it touches only the
 * marks this parser injected, so the author's own asterisks and underscores
 * survive.
 */
internal fun String.clearInlineMarks(): String {
    // Nothing to clear on the overwhelming majority of lines, and scanning for
    // one character beats running two regexes over the whole string — the same
    // shape [stripInlineMarks] already uses.
    if (none { char -> char in MARK_RANGE }) return this

    return replace(REFERENCE_RUN_REGEX) { match -> match.groupValues[1] }
        .replace(INLINE_MARKS_REGEX, "")
}

// Compiled once — all of these run in the per-line hot loop over the whole book
// (parseDocument), so per-line Regex() compilation added up to a large share of
// parse time. Kept at file scope like TABLE_DELIMITER_REGEX above.
private val IMAGE_LINE_REGEX = Regex("""\[\[(.*?)\|(.*?)]]""")
private val CHAPTER_LINE_REGEX = Regex("""\[\[\[chapter\|(\d+)\|(.*)]]]""")
private val TABLE_LINE_REGEX = Regex("""\[\[\[table\|(\d+)]]]""")
/** Separator-like text: "---", "***", "___", also spaced out ("* * *"). */
private val SEPARATOR_TEXT_REGEX = Regex("""^([-*_])(\s*\1){2,}$""")
/**
 * How many items of a phase run between two [yield] calls.
 *
 * Yielding once per line cost a quarter of the whole parse (#39): the work
 * itself is microseconds and the round-trip through the dispatcher is ~166 µs,
 * moving the coroutine between worker threads each time. Cancellation is not
 * what paid for that — [ensureActive] still runs on every item and throws just
 * as promptly; a yield only lets *other* coroutines in, and at 64 items that is
 * still well under 50 ms of work between turns.
 */
private const val YIELD_INTERVAL = 64

// \r as well: a file saved with CRLF keeps it through an XML parse, and
// lines() splits on a lone \r just as it does on \n
private val NEWLINES_REGEX = Regex("[\\r\\n]+")

/** A line break inside a paragraph with the indentation around it: one space. */
private val PARAGRAPH_BREAK_REGEX = Regex("[ \\t]*[\\r\\n]+[ \\t]*")
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
     * Parses a document into [ReaderText] by walking its DOM once (#29): see
     * [DocumentWalk] for what becomes what.
     *
     * @param includeChapter Whether the first visible line becomes the chapter
     *   title when the document has no heading of its own.
     * @param sectionTitles Whether the `<title>` of a `<body>`/`<section>` is a
     *   chapter heading — FB2.
     * @return The document's entries; empty when it yields nothing, or when a
     *   chapter was required and none was found.
     */
    suspend fun parseDocument(
        document: Document,
        zipFile: ZipFile? = null,
        imageEntries: List<ZipEntry>? = null,
        base64Images: Map<String, String>? = null,
        includeChapter: Boolean = true,
        sectionTitles: Boolean = false,
        keepImageBytes: Boolean = true
    ): List<ReaderText> = coroutineScope {
        yield()

        // Images decode in parallel while the text is being walked
        val imageJobs = mutableMapOf<String, Deferred<ReaderImage?>>()
        fun decode(src: String) = imageJobs.getOrPut(src) {
            async(Dispatchers.Default) {
                loadImage(src, zipFile, imageEntries, base64Images, keepImageBytes)
            }
        }

        val walk = DocumentWalk(
            includeChapter = includeChapter,
            sectionTitles = sectionTitles,
            image = { element ->
                when (element.normalName()) {
                    "img" -> element.attr("src")
                        .trim()
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf { src ->
                            src.containsVisibleText() && imageEntries.holds(src)
                        }

                    // FB2 <image> references a <binary> by id; SVG's by path
                    else -> element.attr("xlink:href")
                        .ifBlank { element.attr("l:href") }
                        .ifBlank { element.attr("href") }
                        .trim()
                        .removePrefix("#")
                        .substringAfterLast(File.separator)
                        .lowercase()
                        .let { src -> URLDecoder.decode(src, StandardCharsets.UTF_8.name()) }
                        .takeIf { src ->
                            src.containsVisibleText() && (
                                    base64Images?.containsKey(src) == true ||
                                            imageEntries.holds(src)
                                    )
                        }
                }?.let { src -> decode(src) }
            }
        )

        val body = document.selectFirst("body").run { this ?: document.body() }
        timed("      walk") { walk.run(body, YIELD_INTERVAL) }

        val readerText = ArrayList<ReaderText>(walk.entries.size)
        walk.entries.forEach { entry ->
            when (entry) {
                is DocumentWalk.Entry.Ready -> readerText.add(entry.text)
                is DocumentWalk.Entry.Picture -> entry.image.await()?.let { image ->
                    readerText.add(ReaderText.Image(image, entry.caption))
                }
            }
        }

        yield()

        // "Did this document yield anything", not "does it have text": a page
        // holding only an image — every Calibre-made cover is exactly that — is
        // not an empty document, and dropping it takes the image with it.
        if (
            readerText.isEmpty() ||
            (includeChapter && readerText.none { it is ReaderText.Chapter })
        ) {
            return@coroutineScope emptyList()
        }

        readerText
    }

    /**
     * Renders a footnote body (an FB2 <section> from <body name="notes">) to a
     * formatted [AnnotatedString]: the same walk as the text, its titles left
     * out, its lines kept as paragraphs separated by a blank line. References
     * inside a note stay plain text — a note has no text of its own to jump in.
     */
    suspend fun parseNote(section: Element): AnnotatedString {
        val walk = DocumentWalk(
            includeChapter = false,
            sectionTitles = false,
            dropTitles = true,
            links = false
        )
        walk.run(section, YIELD_INTERVAL)

        val paragraphs = walk.entries.flatMap { entry ->
            when (val text = (entry as? DocumentWalk.Entry.Ready)?.text) {
                is ReaderText.Text -> listOf(text)
                is ReaderText.Poem -> text.lines
                else -> emptyList()
            }
        }.filter { paragraph -> paragraph.line.text.containsVisibleText() }

        return buildAnnotatedString {
            paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append("\n\n")
                append(paragraph.line)
            }
        }
    }

    private fun List<ZipEntry>?.holds(src: String): Boolean =
        this?.any { image -> src == image.name.substringAfterLast(File.separator).lowercase() } == true

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
        base64Images: Map<String, String>?,
        keepImageBytes: Boolean
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

            // Identity always comes from the real bytes, so an image keeps the
            // same id whether or not they are kept — the parse stays cacheable.
            val checksum = CRC32().apply { update(bytes) }.value
            ReaderImage(
                id = "$src-${bytes.size}-$checksum",
                src = src,
                bytes = if (keepImageBytes) bytes else ByteArray(0),
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            e.printStackTrace()
            null
        }
    }
}