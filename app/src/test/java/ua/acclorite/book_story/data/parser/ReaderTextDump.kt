/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole

/**
 * A parse result as plain, diffable text: one entry per line, inline styling
 * written as `{i}…{/i}` around the run it covers.
 *
 * This is what a snapshot of a parse compares, so it holds everything the
 * reader acts on and nothing that changes between two runs of the same parse —
 * a chapter's random id is left out.
 */
fun List<ReaderText>.dump(): String = buildString {
    this@dump.forEach { entry -> appendEntry(entry, indent = "") }
}

private fun StringBuilder.appendEntry(entry: ReaderText, indent: String) {
    append(indent)
    when (entry) {
        is ReaderText.Chapter -> {
            append("Chapter depth=${entry.depth} ").append(entry.title.quoted()).append('\n')
            entry.styledTitle?.let { styled ->
                append(indent).append("  styled ").append(styled.dump()).append('\n')
            }
        }

        is ReaderText.Text -> {
            append("Text")
            if (entry.role != ReaderTextRole.Paragraph) append('[').append(entry.role).append(']')
            append(' ').append(entry.line.dump()).append('\n')
        }

        is ReaderText.Poem -> {
            append("Poem\n")
            entry.lines.forEach { line -> appendEntry(line, "$indent  ") }
        }

        is ReaderText.Table -> {
            append("Table header=${entry.hasHeader} align=${entry.alignments}\n")
            entry.rows.forEach { row ->
                append(indent).append("  |")
                row.forEach { cell -> append(' ').append(cell.dump()).append(" |") }
                append('\n')
            }
        }

        ReaderText.Separator -> append("Separator\n")

        is ReaderText.Image -> {
            val image = entry.image
            append("Image ${image.src} ${image.width}x${image.height} bytes=${image.bytes.size}\n")
            entry.caption?.let { caption -> appendEntry(caption, "$indent  caption ") }
        }
    }
}

/** The text in quotes, with every span and link marked where it starts and ends. */
fun AnnotatedString.dump(): String {
    class Mark(val start: Int, val end: Int, val label: String)

    val raw = buildList {
        spanStyles.forEach { range ->
            range.item.labels().forEach { label -> add(Mark(range.start, range.end, label)) }
        }
        getLinkAnnotations(0, length).forEach { range ->
            val label = when (val link = range.item) {
                is LinkAnnotation.Url -> "url=${link.url}"
                is LinkAnnotation.Clickable -> "link=${link.tag}"
                else -> "link"
            }
            add(Mark(range.start, range.end, label))
        }
    }

    // How a builder happens to cut one styled run into spans is not something
    // the reader can see — "{i}a {/i}{i}b{/i}" renders as "{i}a b{/i}" — so
    // touching runs of the same label are merged before anything is written.
    val marks = raw.groupBy { it.label }.flatMap { (label, group) ->
        group.sortedBy { it.start }.fold(mutableListOf<Mark>()) { merged, mark ->
            val last = merged.lastOrNull()
            if (last != null && mark.start <= last.end) {
                merged[merged.lastIndex] = Mark(last.start, maxOf(last.end, mark.end), label)
            } else {
                merged.add(mark)
            }
            merged
        }
    }

    val out = StringBuilder("\"")
    for (position in 0..length) {
        marks.filter { it.end == position && it.start < position }
            .sortedWith(compareByDescending<Mark> { it.start }.thenByDescending { it.label })
            .forEach { out.append("{/").append(it.label.substringBefore('=')).append('}') }
        marks.filter { it.start == position }
            .sortedWith(compareByDescending<Mark> { it.end }.thenBy { it.label })
            .forEach { mark ->
                out.append('{').append(mark.label).append('}')
                if (mark.end == position) {
                    out.append("{/").append(mark.label.substringBefore('=')).append('}')
                }
            }
        if (position < length) out.appendVisible(text[position])
    }
    return out.append('"').toString()
}

/** One short label per attribute a span sets, so overlapping styles stay readable. */
private fun SpanStyle.labels(): List<String> = buildList {
    if (fontStyle == FontStyle.Italic) add("i")
    fontWeight?.let { weight -> add("w${weight.weight}") }
    textDecoration?.let { decoration ->
        if (decoration.contains(TextDecoration.LineThrough)) add("s")
        if (decoration.contains(TextDecoration.Underline)) add("u")
    }
    when (baselineShift) {
        BaselineShift.Superscript -> add("sup")
        BaselineShift.Subscript -> add("sub")
        null -> Unit
        else -> add("shift=${baselineShift?.multiplier}")
    }
    // Sub/superscript always come with their smaller size; say so only otherwise
    if (fontSize.isSpecified && baselineShift == null) add("size=$fontSize")
    if (fontFamily == FontFamily.Monospace) add("mono")
    if (isEmpty()) add("style")
}

private fun String.quoted(): String = buildString {
    append('"')
    this@quoted.forEach { char -> appendVisible(char) }
    append('"')
}

/**
 * Appends [char], spelling out what would otherwise be invisible or ambiguous
 * in a snapshot: line breaks, tabs, non-breaking spaces, private-use sentinels.
 */
private fun StringBuilder.appendVisible(char: Char) {
    when {
        char == '\n' -> append("\\n")
        char == '\t' -> append("\\t")
        char == '\\' || char == '"' || char == '{' || char == '}' -> append('\\').append(char)
        char == ' ' -> append(char)
        char.isWhitespace() || char.isISOControl() ||
                Character.getType(char) == Character.PRIVATE_USE.toInt() ->
            append("\\u%04X".format(char.code))

        else -> append(char)
    }
}
