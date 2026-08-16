/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Shortest query that is searched at all. One character matches most of a book
 * and says nothing; two is where a search starts to mean something.
 */
const val SEARCH_MIN_QUERY_LENGTH = 2

/** How many items are scanned between cancellation checks; see [findSearchMatches]. */
private const val SEARCH_YIELD_STEP = 64

/**
 * One occurrence of the query, anchored to something the reader can put on
 * screen and highlight.
 *
 * [part] identifies which string of the item carries it, because an item may
 * hold several: a poem is a list of lines and a table a grid of cells. It is 0
 * for everything that holds exactly one — a paragraph, a chapter title, an
 * image caption.
 *
 * [start]/[end] are offsets into that string *as it is rendered*, so a
 * highlight can be laid over it without re-deriving anything.
 */
@Immutable
data class SearchMatch(
    val itemIndex: Int,
    val part: Int = 0,
    val start: Int,
    val end: Int,
    /**
     * The query was found in a footnote, not in the text: [start]/[end] are
     * then the note's *reference* in the text — the only part of a footnote
     * that has a place on the page. The word itself is not visible until the
     * note is opened, which searching deliberately does not do.
     */
    val inNote: Boolean = false
)

/**
 * Every occurrence of [query] in the book, in reading order.
 *
 * A footnote is searched through its references: a note whose text matches
 * contributes one match per place that points at it, and a note nothing points
 * at contributes none — it could not be shown, and a match that cannot be
 * reached must not be counted either.
 *
 * Cancellable and meant for a background dispatcher: the check is every
 * [SEARCH_YIELD_STEP] items rather than every item, which is the difference
 * between a scan and a crawl over a book of tens of thousands of paragraphs.
 */
suspend fun List<ReaderText>.findSearchMatches(
    query: String,
    notes: Map<String, AnnotatedString> = emptyMap()
): List<SearchMatch> {
    val needle = query.trim()
    if (needle.length < SEARCH_MIN_QUERY_LENGTH) return emptyList()

    val matchingNotes = notes.entries
        .filter { (_, note) -> note.text.contains(needle, ignoreCase = true) }
        .map { (id, _) -> id }
        .toSet()

    val matches = mutableListOf<SearchMatch>()
    forEachIndexed { index, entry ->
        if (index % SEARCH_YIELD_STEP == 0) coroutineContext.ensureActive()

        val found = mutableListOf<SearchMatch>()
        when (entry) {
            is ReaderText.Text -> found.collect(entry.line, needle, index)

            is ReaderText.Chapter -> found.collect(
                entry.styledTitle ?: AnnotatedString(entry.title), needle, index
            )

            is ReaderText.Poem -> entry.lines.forEachIndexed { part, line ->
                found.collect(line.line, needle, index, part)
            }

            is ReaderText.Table -> entry.forEachCell { part, cell ->
                found.collect(cell, needle, index, part)
            }

            is ReaderText.Image -> entry.caption?.let { caption ->
                found.collect(caption.line, needle, index)
            }

            is ReaderText.Separator -> Unit
        }

        if (matchingNotes.isNotEmpty()) {
            found.collectNoteAnchors(entry, matchingNotes, index)
        }

        found.sortWith(compareBy({ it.part }, { it.start }))
        matches.addAll(found)
    }
    return matches
}

/**
 * Every cell with the part number that identifies it, which is a position in
 * the *drawn* grid — rows are padded out to the widest one, exactly as the
 * renderer draws them, so a ragged table cannot make the two disagree.
 */
inline fun ReaderText.Table.forEachCell(action: (part: Int, cell: AnnotatedString) -> Unit) {
    val columns = rows.maxOfOrNull { row -> row.size } ?: return
    rows.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { columnIndex, cell ->
            action(rowIndex * columns + columnIndex, cell)
        }
    }
}

/** Direct occurrences of [needle] in one rendered string. */
private fun MutableList<SearchMatch>.collect(
    text: AnnotatedString,
    needle: String,
    itemIndex: Int,
    part: Int = 0
) {
    var from = text.text.indexOf(needle, startIndex = 0, ignoreCase = true)
    while (from >= 0) {
        add(
            SearchMatch(
                itemIndex = itemIndex,
                part = part,
                start = from,
                end = from + needle.length
            )
        )
        from = text.text.indexOf(needle, startIndex = from + needle.length, ignoreCase = true)
    }
}

/**
 * Anchors for footnotes that match: every reference in [entry] pointing at one
 * of [matchingNotes]. An anchor overlapping a direct match is dropped — the
 * same spot on the page must not be stepped onto twice.
 */
private fun MutableList<SearchMatch>.collectNoteAnchors(
    entry: ReaderText,
    matchingNotes: Set<String>,
    itemIndex: Int
) {
    fun anchors(text: AnnotatedString, part: Int) {
        text.getLinkAnnotations(0, text.length).forEach { range ->
            val item = range.item
            if (item !is LinkAnnotation.Clickable) return@forEach
            if (!item.tag.startsWith(NOTE_LINK_TAG_PREFIX)) return@forEach
            if (item.tag.removePrefix(NOTE_LINK_TAG_PREFIX) !in matchingNotes) return@forEach

            val overlapsText = any { match ->
                match.part == part && match.start < range.end && range.start < match.end
            }
            if (overlapsText) return@forEach

            add(
                SearchMatch(
                    itemIndex = itemIndex,
                    part = part,
                    start = range.start,
                    end = range.end,
                    inNote = true
                )
            )
        }
    }

    when (entry) {
        is ReaderText.Text -> anchors(entry.line, 0)
        is ReaderText.Chapter -> entry.styledTitle?.let { anchors(it, 0) }
        is ReaderText.Poem -> entry.lines.forEachIndexed { part, line ->
            anchors(line.line, part)
        }

        is ReaderText.Image -> entry.caption?.let { anchors(it.line, 0) }
        is ReaderText.Table -> entry.forEachCell { part, cell -> anchors(cell, part) }
        // A separator has no text to point from.
        is ReaderText.Separator -> Unit
    }
}
