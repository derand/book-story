/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import java.util.UUID

/**
 * Semantic role of a [ReaderText.Text] paragraph. The paragraph text carries
 * only inline styling; the role tells the renderer what the paragraph *is*,
 * and the renderer decides how it looks (indentation step, font scale).
 * Container context ([ReaderText.Poem]) composes on top: e.g. a title
 * inside a poem is laid out relative to the poem block.
 */
enum class ReaderTextRole {
    Paragraph,

    /** <title> of a <poem>/<epigraph>/<cite> (section titles become chapters). */
    Title,

    /** Paragraph of an <epigraph>. */
    Epigraph,

    /** <text-author> of a poem, epigraph or cite. */
    TextAuthor
}

/**
 * Horizontal alignment of a table column, as stated by the source: the markdown
 * delimiter row (`:---`, `:--:`, `---:`) or an `align` attribute — or an inline
 * `text-align` — on an FB2/HTML `<td>`/`<th>`.
 *
 * The sources say "left" and "right", which map to [Start] and [End] rather
 * than to fixed sides: cells otherwise default to the reading direction, and a
 * table in a right-to-left book would end up disagreeing with its own text.
 */
enum class TableAlignment {
    /** The source states nothing — the renderer keeps its default. */
    Unspecified,
    Start,
    Center,
    End
}

@Immutable
sealed class ReaderText {
    @Immutable
    data class Chapter(
        val id: UUID = UUID.randomUUID(),
        val title: String,
        /** Nesting depth of the chapter: 0 for a top-level chapter. */
        val depth: Int = 0,
        /**
         * The title as it is set in the text, with its inline markup (italic,
         * sub/superscript, footnote references, ...); null when the title is
         * plain. The chapter list and the toolbar always use [title].
         */
        val styledTitle: AnnotatedString? = null
    ) : ReaderText() {
        val nested: Boolean
            get() = depth > 0
    }

    @Immutable
    data class Text(
        val line: AnnotatedString,
        val role: ReaderTextRole = ReaderTextRole.Paragraph
    ) : ReaderText()

    /**
     * An FB2 <poem>: a self-contained block of [lines] laid out as one unit —
     * as wide as its longest line, with a left-aligned interior.
     */
    @Immutable
    data class Poem(
        val lines: List<Text>
    ) : ReaderText()

    /**
     * A table: [rows] of cells, each cell an [AnnotatedString]. The first row
     * is a header when [hasHeader] is set.
     *
     * [alignments] holds one entry per column, in column order. It may be
     * shorter than the widest row — or empty, when the source states nothing —
     * so read it through [alignmentAt].
     */
    @Immutable
    data class Table(
        val rows: List<List<AnnotatedString>>,
        val hasHeader: Boolean,
        val alignments: List<TableAlignment> = emptyList()
    ) : ReaderText() {
        fun alignmentAt(column: Int): TableAlignment =
            alignments.getOrElse(column) { TableAlignment.Unspecified }
    }

    @Immutable
    data object Separator : ReaderText()

    @Immutable
    data class Image(
        val image: ReaderImage,
        val caption: Text? = null
    ) : ReaderText()
}