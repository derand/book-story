/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent

/**
 * Positional hit-test for reference/link annotations: the link (if any) whose
 * visible glyphs are under [position], or null. Finding one and firing it are
 * separate steps ([dispatch]) because a tap is claimed when the finger goes
 * *down* and acted upon when it lifts — everything not claimed here belongs to
 * the reader's own tap handling.
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
internal fun AnnotatedString.linkAt(
    layout: TextLayoutResult,
    position: Offset,
    paddingPx: Float = 0f
): LinkAnnotation? {
    val lineIndex = layout.getLineForVerticalPosition(position.y)
    val lineStart = layout.getLineStart(lineIndex)
    val lineEnd = layout.getLineEnd(lineIndex, visibleEnd = true)
    if (lineEnd <= lineStart) return null
    if (position.y < layout.getLineTop(lineIndex) ||
        position.y > layout.getLineBottom(lineIndex)
    ) return null

    val links = getLinkAnnotations(lineStart, lineEnd)
    if (links.isEmpty()) return null

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

        val item = link.item
        if (item.actionable) return item
    }
    return null
}

/**
 * A single positional handler for every link of [text], because Compose's
 * per-link touch boxes are misaligned on justified lines (see [linkAt]). It
 * claims the gesture only when a link is genuinely under the finger; every
 * other tap is left unconsumed for the reader to read as a zone. [layout] is
 * the text's latest layout, read when the finger goes down.
 */
internal fun Modifier.referenceTaps(
    text: AnnotatedString,
    layout: () -> TextLayoutResult?,
    uriHandler: UriHandler,
    enabled: Boolean
): Modifier {
    if (!enabled) return this

    return pointerInput(text) {
        // Enlarge the reference hit boxes so the small superscript footnote
        // markers stay easy to tap.
        val linkPadding = 12.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val result = layout() ?: return@awaitEachGesture
            if (text.linkAt(result, down.position, linkPadding) == null) {
                return@awaitEachGesture
            }

            down.consume()
            // A press that travelled was going somewhere, not opening
            // anything: with free scrolling given up nobody consumes that
            // movement, so the drag would otherwise arrive here as a tap on a
            // link.
            val up = awaitPress(down)
            if (up !is PressOutcome.Tap) return@awaitEachGesture
            up.change.consume()
            text.linkAt(result, up.change.position, linkPadding)?.dispatch(uriHandler)
        }
    }
}

/**
 * One rendered line of an entry: its references wired to [openNote] and the
 * search highlights of [part] laid over it. Remembered, as both steps copy the
 * string.
 */
@Composable
internal fun rememberRenderedLine(
    line: AnnotatedString,
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    fontColor: Color,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    part: Int = 0
): AnnotatedString = remember(line, part, searchMatches, currentSearchMatch, fontColor, openNote) {
    line
        .withReferenceListeners { tag -> openNote(ReaderEvent.OnOpenNote(tag)) }
        .withSearchHighlights(
            matches = searchMatches,
            current = currentSearchMatch,
            fontColor = fontColor,
            part = part
        )
}

/** Fires the link: its own listener, or — for a bare URL — the platform's. */
internal fun LinkAnnotation.dispatch(uriHandler: UriHandler) {
    when (this) {
        // External links carry no listener; open them the way Compose's
        // default link handler would.
        is LinkAnnotation.Url -> linkInteractionListener?.onClick(this)
            ?: uriHandler.openUri(url)

        else -> linkInteractionListener?.onClick(this)
    }
}

/**
 * Whether firing this link would do anything. A note/anchor reference always
 * would (its listener is attached at render time), a URL always would, and
 * anything else only with a listener of its own — without one it is not a tap
 * target at all, and the tap belongs to the reader.
 */
private val LinkAnnotation.actionable: Boolean
    get() = when (this) {
        is LinkAnnotation.Clickable, is LinkAnnotation.Url -> true
        else -> linkInteractionListener != null
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
