/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult

/**
 * Positional hit-test for reference/link annotations. Finds the link (if any)
 * whose visible glyphs are under [position] and fires it, returning `true`
 * when a link was hit (so the caller can suppress its fall-through action,
 * e.g. toggling the reader menu).
 *
 * Why this is needed: inter-word justification is applied only when a line is
 * *drawn*, so every layout query API ([TextLayoutResult.getBoundingBox],
 * [TextLayoutResult.getOffsetForPosition], …) reports character positions as
 * if the line were NOT justified — to the LEFT of the visible glyphs, by the
 * accumulated space expansion. Compose's own per-link touch boxes come from
 * getBoundingBox, so on a justified line they miss the glyph the user taps.
 *
 * To match what the reader draws, this reconstructs each link's justified box:
 * it takes the un-justified box and shifts it right by the extra width the
 * platform spreads across the inter-word spaces preceding the link, exactly as
 * the draw pass does. [paddingPx] enlarges the hit box horizontally so the tiny
 * superscript footnote markers stay comfortably tappable.
 */
internal fun AnnotatedString.dispatchLinkAt(
    layout: TextLayoutResult,
    position: Offset,
    uriHandler: UriHandler,
    paddingPx: Float = 0f
): Boolean {
    val lineIndex = layout.getLineForVerticalPosition(position.y)
    val lineStart = layout.getLineStart(lineIndex)
    val lineEnd = layout.getLineEnd(lineIndex, visibleEnd = true)
    if (lineEnd <= lineStart) return false
    if (position.y < layout.getLineTop(lineIndex) ||
        position.y > layout.getLineBottom(lineIndex)
    ) return false

    val links = getLinkAnnotations(lineStart, lineEnd)
    if (links.isEmpty()) return false

    // Extra width the draw pass spreads across this line's inter-word spaces.
    // On the last (or otherwise un-justified) line getLineRight equals the
    // natural right edge, so the expansion is zero and boxes are left as-is.
    val naturalRight = layout.getBoundingBox((lineEnd - 1).coerceAtLeast(lineStart)).right
    val justifiedRight = layout.getLineRight(lineIndex)
    val spaceCount = text.substring(lineStart, lineEnd).count { it == ' ' }
    val extraPerSpace =
        if (spaceCount > 0 && justifiedRight > naturalRight) {
            (justifiedRight - naturalRight) / spaceCount
        } else 0f

    for (link in links) {
        val s = link.start.coerceAtLeast(lineStart)
        val e = link.end.coerceAtMost(lineEnd)
        if (e <= s) continue

        val shift = text.substring(lineStart, s).count { it == ' ' } * extraPerSpace
        val left = layout.getBoundingBox(s).left + shift
        val right = layout.getBoundingBox(e - 1).right + shift
        if (position.x < left - paddingPx || position.x > right + paddingPx) continue

        return when (val item = link.item) {
            is LinkAnnotation.Clickable -> {
                item.linkInteractionListener?.onClick(item)
                true
            }

            is LinkAnnotation.Url -> {
                // External links carry no listener; open them the way Compose's
                // default link handler would.
                item.linkInteractionListener?.onClick(item) ?: uriHandler.openUri(item.url)
                true
            }

            else -> {
                item.linkInteractionListener?.onClick(item)
                item.linkInteractionListener != null
            }
        }
    }
    return false
}

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
