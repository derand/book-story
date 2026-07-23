/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString

/**
 * Tag prefixes of clickable in-text references ([androidx.compose.ui.text.LinkAnnotation.Clickable]).
 * The rest of the tag is the referenced element id.
 */
const val NOTE_LINK_TAG_PREFIX = "note:"
const val ANCHOR_LINK_TAG_PREFIX = "anchor:"

/**
 * Result of parsing a book file: the text itself plus side content that is
 * not part of the reading flow.
 */
@Immutable
data class ParsedText(
    val text: List<ReaderText>,

    /**
     * Footnote texts by id (FB2 <body name="notes">/<body name="comments">),
     * shown in a popup when the in-text note reference is tapped.
     */
    val notes: Map<String, AnnotatedString> = emptyMap()
) {
    companion object {
        val EMPTY = ParsedText(text = emptyList())
    }
}
