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

/** How the whitespace of the book's source becomes the whitespace of a line. */
internal enum class Spacing {
    /**
     * A paragraph: a run of whitespace holding a line break is one space, as
     * in HTML; any other run is kept as typed, tabs becoming spaces. Trimmed.
     */
    Paragraph,

    /** A heading flattened onto one line: every run of whitespace is one space. Trimmed. */
    Line,

    /**
     * A table cell: like [Line], but only ASCII whitespace collapses — a
     * non-breaking space inside a cell is kept.
     */
    Cell,

    /** `<pre>`: line breaks and indentation are kept, a tab is four spaces. Not trimmed. */
    Preformatted
}

/**
 * The text of one line under construction, with the styles and links that
 * cover parts of it — what an [AnnotatedString.Builder] would be, if a run
 * could stay open across the lines it is cut into, and if the whitespace could
 * be settled once the line is complete rather than character by character.
 *
 * A run is opened by [push] and closed by [pop], in the nesting order of the
 * elements that caused them. [take] cuts the line at the current position:
 * the runs still open are closed there and carry on at the start of the next
 * line, which is how an `<em>` wrapping two paragraphs styles both.
 */
internal class InlineText {

    private class Run(val item: Any, var start: Int, val end: Int = -1)

    private val text = StringBuilder()
    private val closed = ArrayList<Run>()
    private val open = ArrayList<Run>()

    fun isEmpty(): Boolean = text.isEmpty()

    fun append(value: String) {
        text.append(value)
    }

    /** Opens a run of [item] — a [SpanStyle] or a [LinkAnnotation] — at the current position. */
    fun push(item: Any) {
        open.add(Run(item, text.length))
    }

    /** Closes the innermost open run. */
    fun pop() {
        close(open.removeAt(open.lastIndex))
    }

    /** Appends [value] covered by [link] as a whole. */
    fun appendLink(value: String, link: LinkAnnotation) {
        val start = text.length
        text.append(value)
        if (text.length > start) closed.add(Run(link, start, text.length))
    }

    private fun close(run: Run) {
        if (text.length > run.start) closed.add(Run(run.item, run.start, text.length))
    }

    /**
     * The line so far, its whitespace settled by [spacing]; the builder is left
     * empty, with the runs still open restarting at its beginning.
     */
    fun take(spacing: Spacing): AnnotatedString {
        open.forEach { run ->
            close(run)
            run.start = 0
        }
        return build(spacing).also {
            text.setLength(0)
            closed.clear()
        }
    }

    private fun build(spacing: Spacing): AnnotatedString {
        val source = text
        val length = source.length
        val out = StringBuilder(length)
        // Where each source position lands in the output, so the runs can follow
        val position = IntArray(length + 1)

        var index = 0
        while (index < length) {
            val char = source[index]

            if (spacing != Spacing.Preformatted && char.isSpaceFor(spacing)) {
                var end = index + 1
                var hasBreak = char == '\n' || char == '\r'
                while (end < length && source[end].isSpaceFor(spacing)) {
                    hasBreak = hasBreak || source[end] == '\n' || source[end] == '\r'
                    end++
                }

                if (spacing != Spacing.Paragraph || hasBreak) {
                    position[index] = out.length
                    out.append(' ')
                    for (skipped in index + 1 until end) position[skipped] = out.length
                } else {
                    for (kept in index until end) {
                        position[kept] = out.length
                        out.append(if (source[kept] == '\t') ' ' else source[kept])
                    }
                }
                index = end
                continue
            }

            position[index] = out.length
            when {
                spacing == Spacing.Preformatted && char == '\t' -> out.append("    ")
                spacing == Spacing.Preformatted && char == '\r' -> {
                    out.append('\n')
                    if (index + 1 < length && source[index + 1] == '\n') {
                        index++
                        position[index] = out.length
                    }
                }

                else -> out.append(char)
            }
            index++
        }
        position[length] = out.length

        var start = 0
        var end = out.length
        if (spacing != Spacing.Preformatted) {
            while (start < end && out[start].isWhitespace()) start++
            while (end > start && out[end - 1].isWhitespace()) end--
        }

        val builder = AnnotatedString.Builder(out.substring(start, end))
        // Outer runs first: a later span wins where two set the same attribute,
        // and the inner element is the one that should
        closed.sortedWith(compareBy<Run> { it.start }.thenByDescending { it.end })
            .forEach { run ->
                val from = (position[run.start] - start).coerceIn(0, end - start)
                val to = (position[run.end] - start).coerceIn(0, end - start)
                if (to <= from) return@forEach

                when (val item = run.item) {
                    is SpanStyle -> builder.addStyle(item, from, to)
                    is LinkAnnotation.Url -> builder.addLink(item, from, to)
                    is LinkAnnotation.Clickable -> builder.addLink(item, from, to)
                }
            }
        return builder.toAnnotatedString()
    }

    private fun Char.isSpaceFor(spacing: Spacing): Boolean = when (spacing) {
        Spacing.Paragraph -> this == ' ' || this == '\t' || this == '\n' || this == '\r'
        Spacing.Line -> isWhitespace()
        // What the regex \s matches
        Spacing.Cell -> this == ' ' || this in '\t'..'\r'
        Spacing.Preformatted -> false
    }
}
