/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.TableAlignment

/** A GFM table delimiter row, e.g. "| --- | :--: |". */
private val TABLE_DELIMITER_REGEX =
    Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$""")

/** How a fenced code block opens: up to three spaces, then the fence and its info string. */
private val FENCE_OPENING_REGEX = Regex("""^( {0,3})(`{3,}(?=[^`]*$)|~{3,}).*$""")

/** The spaces in a tab stop of a code block. */
private const val CODE_TAB = "    "

/** A line of markdown text, or a block that several lines made up. */
sealed interface MarkdownLine {
    data class Text(val line: String) : MarkdownLine

    /** The verbatim lines of a fenced code block, fences excluded. */
    data class Code(val lines: List<String>) : MarkdownLine
    data class Table(val table: ReaderText.Table) : MarkdownLine
}

/**
 * Finds the blocks that span lines in the line stream and returns each as one:
 * a fenced code block, which runs to the end of the file when nothing closes it,
 * and a GFM pipe table (a header row, a delimiter row of dashes, then body rows),
 * whose cell text is parsed with [markdownParser]. Every other line is passed
 * through as it is. Fences go first: a `|` inside a code block is not a table.
 */
fun splitMarkdownBlocks(
    lines: List<String>,
    markdownParser: MarkdownParser
): List<MarkdownLine> {
    val result = mutableListOf<MarkdownLine>()
    var i = 0
    while (i < lines.size) {
        val header = lines[i]
        val delimiter = lines.getOrNull(i + 1)

        val opening = FENCE_OPENING_REGEX.matchEntire(header)
        if (opening != null) {
            val (indent, fence) = opening.destructured
            val code = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && !lines[j].closesFence(fence)) {
                code.add(lines[j].codeLine(indent.length))
                j++
            }
            result.add(MarkdownLine.Code(code))
            i = j + 1
            continue
        }

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
            val alignments = splitTableRow(delimiter).map { it.delimiterAlignment() }
            result.add(
                MarkdownLine.Table(
                    ReaderText.Table(rows, hasHeader = true, alignments = alignments)
                )
            )
            i = j
        } else {
            result.add(MarkdownLine.Text(header))
            i++
        }
    }
    return result
}

private fun String.closesFence(fence: String): Boolean {
    val run = trimEnd().trimStart(' ')
    return length - trimStart(' ').length <= 3 &&
            run.length >= fence.length &&
            run.all { it == fence[0] }
}

/** A line of a code block: the fence's own indentation is not part of the code. */
private fun String.codeLine(indent: Int): String =
    drop(takeWhile { it == ' ' }.length.coerceAtMost(indent))
        .replace("\t", CODE_TAB)
        .trimEnd()

/**
 * Reads the alignment out of one cell of a markdown delimiter row: a colon
 * marks the side the text is pulled to — `:---` start, `---:` end, `:--:`
 * both, i.e. centred. A plain `---` states nothing.
 */
private fun String.delimiterAlignment(): TableAlignment {
    val cell = trim()
    val start = cell.startsWith(':')
    val end = cell.endsWith(':')
    return when {
        start && end -> TableAlignment.Center
        end -> TableAlignment.End
        start -> TableAlignment.Start
        else -> TableAlignment.Unspecified
    }
}

/** Splits a markdown table row into cells, dropping the outer pipes. */
private fun splitTableRow(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith("|")) row = row.substring(1)
    if (row.endsWith("|")) row = row.dropLast(1)
    return row.split("|").map { it.trim() }
}
