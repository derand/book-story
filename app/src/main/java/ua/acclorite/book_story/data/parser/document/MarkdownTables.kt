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

/** A line of markdown text, or a pipe table that several lines made up. */
sealed interface MarkdownLine {
    data class Text(val line: String) : MarkdownLine
    data class Table(val table: ReaderText.Table) : MarkdownLine
}

/**
 * Finds GFM pipe tables in the line stream (a header row, a delimiter row
 * of dashes, then body rows) and returns each as one parsed table; every other
 * line is passed through as it is. Cell text is parsed with [markdownParser].
 */
fun splitMarkdownTables(
    lines: List<String>,
    markdownParser: MarkdownParser
): List<MarkdownLine> {
    val result = mutableListOf<MarkdownLine>()
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
