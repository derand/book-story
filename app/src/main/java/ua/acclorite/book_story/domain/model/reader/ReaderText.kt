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

@Immutable
sealed class ReaderText {
    @Immutable
    data class Chapter(
        val id: UUID = UUID.randomUUID(),
        val title: String,
        val nested: Boolean
    ) : ReaderText()

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

    @Immutable
    data object Separator : ReaderText()

    @Immutable
    data class Image(
        val image: ReaderImage,
        val caption: Text? = null
    ) : ReaderText()
}