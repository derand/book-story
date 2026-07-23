/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation

/**
 * Attaches [onClick] to the clickable reference annotations (note/anchor
 * links) of the line. The parser creates them without a listener, as parsed
 * text is data and cannot reach the reader's event handlers.
 */
internal fun AnnotatedString.withReferenceListeners(
    onClick: (tag: String) -> Unit
): AnnotatedString {
    if (!hasLinkAnnotations(0, length)) return this

    return mapAnnotations { range ->
        val item = range.item
        if (item is LinkAnnotation.Clickable && item.linkInteractionListener == null) {
            AnnotatedString.Range(
                item = LinkAnnotation.Clickable(
                    tag = item.tag,
                    styles = item.styles,
                    linkInteractionListener = { onClick(item.tag) }
                ),
                start = range.start,
                end = range.end,
                tag = range.tag
            )
        } else {
            range
        }
    }
}
