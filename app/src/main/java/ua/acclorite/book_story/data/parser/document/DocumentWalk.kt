/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.yield
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ua.acclorite.book_story.core.helpers.containsVisibleText
import ua.acclorite.book_story.domain.model.reader.ANCHOR_LINK_TAG_PREFIX
import ua.acclorite.book_story.domain.model.reader.NOTE_LINK_TAG_PREFIX
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.TableAlignment
import kotlin.coroutines.coroutineContext

private val BOLD = SpanStyle(fontWeight = FontWeight.Medium)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
private val STRIKETHROUGH = SpanStyle(textDecoration = TextDecoration.LineThrough)
private val SUBSCRIPT = SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
private val SUPERSCRIPT = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
private val MONOSPACE = SpanStyle(fontFamily = FontFamily.Monospace)

private val URL_LINK_STYLES = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
private val NOTE_LINK_STYLES = TextLinkStyles(SUPERSCRIPT)
private val ANCHOR_LINK_STYLES = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))

/** An FB2 <empty-line/> and the gap between two stanzas. */
private const val BLANK_LINE = "\u00A0"

/**
 * Elements that start a line of their own — HTML's block elements and FB2's.
 * Anything else is inline: its text continues the line it is in.
 */
private val BLOCK_ELEMENTS = setOf(
    // HTML
    "address", "article", "aside", "blockquote", "body", "caption", "center", "dd",
    "details", "dialog", "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure",
    "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "html",
    "li", "main", "menu", "nav", "ol", "p", "pre", "section", "summary", "tbody", "td",
    "tfoot", "th", "thead", "tr", "ul",
    // FB2
    "annotation", "cite", "epigraph", "fictionbook", "poem", "stanza", "subtitle",
    "text-author", "title", "v"
)

/** Elements a heading flattened onto one line or a table cell cannot hold. */
private val NOT_INLINE_ELEMENTS = setOf("empty-line", "hr", "image", "img", "table")

/** Separator-like text: "---", "***", "___", also spaced out ("* * *"). */
private val SEPARATOR_TEXT_REGEX = Regex("""^([-*_])(\s*\1){2,}$""")

/** The style an inline element — or a heading — puts on its text, if any. */
private fun styleOf(name: String): SpanStyle? = when (name) {
    "b", "strong", "h1", "h2", "h3", "h4", "h5", "h6" -> BOLD
    // <i> as well as <em>: EPUBs converted from print use it for most of their
    // italics. FB2 spells it <emphasis>.
    "em", "i", "emphasis" -> ITALIC
    "strikethrough", "s", "del", "strike" -> STRIKETHROUGH
    "sub" -> SUBSCRIPT
    "sup" -> SUPERSCRIPT
    "code" -> MONOSPACE
    // <u> on purpose: the reader draws links with an underline
    else -> null
}

/**
 * Visits [root] and everything under it in document order, without recursion
 * — a malformed HTML book can nest thousands of unclosed tags. [enter] says
 * whether to go into a node's children; [exit] is called only for the nodes
 * entered that way, after their children.
 */
private inline fun walk(root: Node, enter: (Node) -> Boolean, exit: (Node) -> Unit) {
    var node = root
    var depth = 0
    while (true) {
        if (enter(node)) {
            if (node.childNodeSize() > 0) {
                node = node.childNode(0)
                depth++
                continue
            }
            exit(node)
        }

        while (true) {
            if (depth == 0) return
            val next = node.nextSibling()
            if (next != null) {
                node = next
                break
            }
            node = node.parentNode()!!
            depth--
            exit(node)
        }
    }
}

/** The target of a link: FB2 spells the attribute through the XLink namespace. */
private fun Element.linkTarget(): String =
    attr("xlink:href").ifBlank { attr("l:href") }.ifBlank { attr("href") }.trim()

/**
 * Where this link leads, as the annotation the reader acts on — or null for a
 * link it does not follow (a relative path, `mailto:`, a blank one).
 */
private fun Element.linkAnnotation(): LinkAnnotation? {
    val target = linkTarget()
    return when {
        target.startsWith("http") -> LinkAnnotation.Url(
            url = if (target.startsWith("http://")) {
                target.replaceFirst("http://", "https://")
            } else target,
            styles = URL_LINK_STYLES
        )

        // An internal reference: a footnote (<a type="note">) or a plain anchor
        target.startsWith("#") -> {
            val id = target.removePrefix("#").trim().lowercase()
            val isNote = attr("type") == "note"
            LinkAnnotation.Clickable(
                tag = (if (isNote) NOTE_LINK_TAG_PREFIX else ANCHOR_LINK_TAG_PREFIX) + id,
                styles = if (isNote) NOTE_LINK_STYLES else ANCHOR_LINK_STYLES,
                // The listener is injected at render time — parsed text is
                // data and cannot reach the reader's event handlers
                linkInteractionListener = null
            )
        }

        else -> null
    }
}

/**
 * Appends what an `<a>` element holds to [line]. An external link keeps the
 * styling inside it; an internal reference is one plain run — a note number
 * wrapped in `<strong>` is still just the marker. The result says whether the
 * element's children still have to be walked, and whether a run was opened.
 */
private fun InlineText.enterLink(element: Element): LinkEntry {
    // Nothing to tap on: whatever is inside (an image) is walked as usual
    if (element.wholeText().isBlank()) return LinkEntry.Transparent
    return when (val link = element.linkAnnotation()) {
        is LinkAnnotation.Url -> {
            push(link)
            LinkEntry.Opened
        }

        is LinkAnnotation.Clickable -> {
            appendLink(element.wholeText(), link)
            LinkEntry.Consumed
        }

        else -> LinkEntry.Transparent
    }
}

private enum class LinkEntry { Transparent, Opened, Consumed }

/**
 * [element]'s content as one line: blocks inside it are joined with a space and
 * whatever cannot sit inside a line — images, tables, rules — is left out.
 * A chapter heading and a table cell are made this way.
 */
private fun inlineLine(
    element: Element,
    spacing: Spacing,
    outer: SpanStyle? = null,
    links: Boolean = true
): AnnotatedString {
    val line = InlineText()
    outer?.let { style -> line.push(style) }

    // How many runs each entered element opened, in entry order
    val opened = ArrayList<Int>()
    walk(
        root = element,
        enter = enter@{ node ->
            if (node === element) {
                opened.add(0)
                return@enter true
            }
            when (node) {
                is TextNode -> {
                    line.append(node.wholeText)
                    false
                }

                is Element -> {
                    val name = node.normalName()
                    when {
                        name in NOT_INLINE_ELEMENTS -> false
                        // Descended into all the same: not-quite-XML leaves a
                        // <br> unclosed, holding the text that follows it
                        name == "br" -> {
                            line.append(" ")
                            opened.add(0)
                            true
                        }

                        name == "a" && links -> when (line.enterLink(node)) {
                            LinkEntry.Consumed -> false
                            LinkEntry.Opened -> {
                                opened.add(1)
                                true
                            }

                            LinkEntry.Transparent -> {
                                opened.add(0)
                                true
                            }
                        }

                        else -> {
                            val style = styleOf(name)
                            style?.let { line.push(it) }
                            opened.add(if (style != null) 1 else 0)
                            true
                        }
                    }
                }

                else -> false
            }
        },
        exit = { node ->
            repeat(opened.removeAt(opened.lastIndex)) { line.pop() }
            if (node !== element && node is Element && node.normalName() in BLOCK_ELEMENTS) {
                line.append(" ")
            }
        }
    )
    return line.take(spacing)
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
 * Reads the alignment of an FB2/HTML `<td>`/`<th>`: the `align` attribute
 * FB2 2.0 defines, falling back to an inline `text-align`, which is how an
 * EPUB usually says it (`align` has been deprecated HTML since HTML 4).
 */
private fun Element.tableAlignment(): TableAlignment {
    val stated = attr("align").ifBlank {
        attr("style").substringAfter("text-align:", "").substringBefore(';')
    }
    return when (stated.trim().lowercase()) {
        "left", "start" -> TableAlignment.Start
        "center" -> TableAlignment.Center
        "right", "end" -> TableAlignment.End
        // Anything else, "justify" included: nothing this renderer can say.
        else -> TableAlignment.Unspecified
    }
}

/**
 * A table read whole: rows of cells, each cell one line. Nested tables belong
 * to the outer one. Null for a table with no rows.
 */
private fun Element.readTable(): ReaderText.Table? {
    // A column takes its alignment from the first cell that states one —
    // usually the header.
    val alignments = mutableListOf<TableAlignment>()
    val rows = select("tr").mapNotNull { row ->
        val cells = row.select("th, td")
        if (cells.isEmpty()) return@mapNotNull null
        cells.forEachIndexed { column, cell ->
            while (alignments.size <= column) alignments.add(TableAlignment.Unspecified)
            if (alignments[column] == TableAlignment.Unspecified) {
                alignments[column] = cell.tableAlignment()
            }
        }
        cells.map { cell -> inlineLine(cell, Spacing.Cell) }
    }
    if (rows.isEmpty()) return null

    val hasHeader = selectFirst("tr")?.selectFirst("th") != null
    return ReaderText.Table(rows, hasHeader, alignments)
}

/**
 * One pass over a document's DOM that emits [ReaderText] as it goes (#29):
 * a line is built from the text nodes and inline elements between two block
 * boundaries, with the styles of the elements it sits in. Nothing is written
 * back into the tree, and the book's text never passes through a markup
 * language on its way — an asterisk the author typed is an asterisk.
 *
 * @param includeChapter Whether the first visible line becomes the chapter
 *   title when the document has no heading of its own (HTML), rather than the
 *   chapter coming from elsewhere (an EPUB's TOC).
 * @param sectionTitles Whether the `<title>` of a `<body>`/`<section>` is a
 *   chapter heading (FB2). Otherwise a `<title>` is a bold line of the text.
 * @param dropTitles Whether every `<title>` is left out (a footnote body).
 * @param links Whether links become tappable.
 * @param image Resolves an `<img>`/`<image>` to the image it shows — decoding
 *   in the background — or null when the book does not hold it.
 */
internal class DocumentWalk(
    private val includeChapter: Boolean,
    private val sectionTitles: Boolean,
    private val dropTitles: Boolean = false,
    private val links: Boolean = true,
    private val image: (Element) -> Deferred<ReaderImage?>? = { null }
) {
    /** An entry of the result — an image is still decoding while the walk goes on. */
    sealed interface Entry {
        class Ready(val text: ReaderText) : Entry
        class Picture(val image: Deferred<ReaderImage?>, val caption: ReaderText.Text?) : Entry
    }

    /** What an entered element has to undo, or still to do, when the walk leaves it. */
    private class Frame(
        val name: String,
        val block: Boolean = false,
        val runs: Int = 0,
        val role: Boolean = false
    )

    val entries = mutableListOf<Entry>()

    private val line = InlineText()
    private val roles = ArrayList<ReaderTextRole>()
    private val frames = ArrayList<Frame>()
    private var preDepth = 0
    private var chapterAdded = false

    /** Non-null inside a <poem>: its lines are collected here. */
    private var poem: MutableList<ReaderText.Text>? = null

    /**
     * Walks [root]. Cancellation is checked on every node, so a parse the
     * reader walked away from still stops at once; the dispatcher round-trip
     * that lets other coroutines run is what happens only every
     * [yieldInterval] nodes.
     */
    suspend fun run(root: Element, yieldInterval: Int) {
        val job = coroutineContext.job
        var sinceYield = 0

        walk(
            root = root,
            enter = { node ->
                job.ensureActive()
                if (++sinceYield >= yieldInterval) {
                    sinceYield = 0
                    yield()
                }
                enter(node)
            },
            exit = { node -> exit(node as Element) }
        )
        flush()
    }

    private fun enter(node: Node): Boolean = when (node) {
        is TextNode -> {
            line.append(node.wholeText)
            false
        }

        is Element -> enterElement(node)
        // Comments; and the content of <script>/<style> in an HTML parse
        else -> false
    }

    private fun enterElement(element: Element): Boolean {
        val name = element.normalName()
        when (name) {
            // <br>, <hr>, <img> and <image> are still descended into: in
            // not-quite-XML an unclosed one holds the text that follows it
            "br" -> {
                flush()
                frames.add(Frame(name))
                return true
            }

            "hr" -> {
                flush()
                entries.add(Entry.Ready(ReaderText.Separator))
                frames.add(Frame(name))
                return true
            }

            "img", "image" -> {
                emitImage(element, withCaption = name == "img")
                frames.add(Frame(name))
                return true
            }

            "empty-line" -> {
                flush()
                add(ReaderText.Text(AnnotatedString(BLANK_LINE)))
                return false
            }

            "table" -> {
                flush()
                element.readTable()?.let { table -> entries.add(Entry.Ready(table)) }
                return false
            }

            "title" -> {
                flush()
                enterTitle(element)
                return false
            }

            "a" -> if (links) {
                when (line.enterLink(element)) {
                    LinkEntry.Consumed -> return false
                    LinkEntry.Opened -> {
                        frames.add(Frame(name, runs = 1))
                        return true
                    }

                    LinkEntry.Transparent -> Unit
                }
            }
        }

        val block = name in BLOCK_ELEMENTS
        if (block) flush()

        var runs = 0
        fun open(style: SpanStyle) {
            line.push(style)
            runs++
        }

        var role: ReaderTextRole? = null
        val parent = element.parent()?.normalName()
        when (name) {
            // A separator-only subtitle ("* * *", "---") is a scene break, not
            // a heading: it stays literal text
            "subtitle" -> if (!element.wholeText().trim().matches(SEPARATOR_TEXT_REGEX)) {
                open(ITALIC)
                open(BOLD)
            }

            "text-author" -> {
                role = ReaderTextRole.TextAuthor
                open(ITALIC)
            }

            // FB2 <epigraph>/<cite> are conventionally set in italic
            "p" -> when (parent) {
                "epigraph" -> {
                    role = ReaderTextRole.Epigraph
                    open(ITALIC)
                }

                "cite" -> open(ITALIC)
            }

            "poem" -> poem = mutableListOf()
            "pre" -> preDepth++
            else -> styleOf(name)?.let { style -> open(style) }
        }
        role?.let { roles.add(it) }

        frames.add(Frame(name, block = block, runs = runs, role = role != null))
        return true
    }

    private fun exit(element: Element) {
        val frame = frames.removeAt(frames.lastIndex)
        // The line ends inside the element, so it still carries the element's role
        if (frame.block) flush()
        repeat(frame.runs) { line.pop() }
        if (frame.role) roles.removeAt(roles.lastIndex)

        when (frame.name) {
            "pre" -> preDepth--

            // A blank line between stanzas, but not after the last one
            "stanza" -> if (element.nextElementSibling()?.normalName() == "stanza") {
                add(ReaderText.Text(AnnotatedString(BLANK_LINE)))
            }

            "poem" -> {
                poem?.takeIf { it.isNotEmpty() }?.let { lines ->
                    entries.add(Entry.Ready(ReaderText.Poem(lines.toList())))
                }
                poem = null
            }
        }
    }

    private fun enterTitle(title: Element) {
        if (dropTitles) return

        val parent = title.parent()?.normalName()
        if (sectionTitles && (parent == "body" || parent == "section")) {
            if (!includeChapter) return

            // The title keeps its inline markup; the chapter list gets the plain text
            val styled = inlineLine(title, Spacing.Line, links = links)
            val plain = styled.plainTitle()
            if (!plain.containsVisibleText()) return

            entries.add(
                Entry.Ready(
                    ReaderText.Chapter(
                        title = plain,
                        // Depth 0 = a title of a <body> or a top-level <section>
                        depth = (title.parents().count { it.normalName() == "section" } - 1)
                            .coerceAtLeast(0),
                        styledTitle = styled.takeIf { it.hasInlineMarkup() }
                    )
                )
            )
            chapterAdded = true
            return
        }

        // The title of an FB2 <poem>/<epigraph>/<cite>: one bold line
        emit(inlineLine(title, Spacing.Line, outer = BOLD, links = links), ReaderTextRole.Title)
    }

    private fun emitImage(element: Element, withCaption: Boolean) {
        val decoding = image(element) ?: return
        // An image inside a paragraph splits it: text before, image, text after
        flush()

        val caption = if (withCaption) {
            element.attr("alt").trim().takeIf { alt -> alt.containsVisibleText() }
                ?.let { alt ->
                    ReaderText.Text(
                        AnnotatedString.Builder(alt).apply { addStyle(ITALIC, 0, alt.length) }
                            .toAnnotatedString()
                    )
                }
        } else null
        entries.add(Entry.Picture(decoding, caption))
    }

    /** Ends the line being built, if it holds anything, and emits it. */
    private fun flush() {
        if (line.isEmpty()) return
        val role = roles.lastOrNull() ?: ReaderTextRole.Paragraph

        if (preDepth == 0) {
            emit(line.take(Spacing.Paragraph), role)
            return
        }

        // <pre>: one entry per line, the indentation kept
        val block = line.take(Spacing.Preformatted)
        var start = 0
        while (start <= block.length) {
            val end = block.text.indexOf('\n', start).let { if (it < 0) block.length else it }
            var trimmedEnd = end
            while (trimmedEnd > start && block[trimmedEnd - 1].isWhitespace()) trimmedEnd--
            emit(block.subSequence(start, trimmedEnd), role)
            start = end + 1
        }
    }

    private fun emit(text: AnnotatedString, role: ReaderTextRole) {
        if (!text.text.containsVisibleText()) return

        // A line of separator characters ("* * *", "---") is a scene break the
        // author typed: text like any other, but never a chapter title
        if (
            includeChapter && !chapterAdded && poem == null &&
            !text.text.matches(SEPARATOR_TEXT_REGEX)
        ) {
            entries.add(0, Entry.Ready(ReaderText.Chapter(title = text.text.trim())))
            chapterAdded = true
            return
        }

        add(ReaderText.Text(text, role))
    }

    private fun add(text: ReaderText.Text) {
        poem?.add(text) ?: entries.add(Entry.Ready(text))
    }
}
