/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How many lines of the page just read stay on screen after a turn. */
internal const val PAGE_TURN_OVERLAP_LINES = 1.5f

/** Long enough for the eye to follow the text, short enough not to feel slow. */
private const val PAGE_TURN_DURATION_MS = 250

/** A turn never covers less than this, however the step is configured. */
private const val MIN_PAGE_FRACTION = 0.1f

/**
 * How far one page turn scrolls, in pixels.
 *
 * The reader scrolls continuously, so a "page" is only ever a screenful — but a
 * screenful cuts the bottom line in half, and half a line that never comes back
 * is text the reader silently skipped. The step is therefore capped so that at
 * least [overlap] survives the turn: whatever the bottom edge cut off arrives at
 * the top whole. True line alignment is not available here — a paragraph is one
 * lazy item and its text layout does not reach the list state — so this is the
 * approximation that keeps the property that matters.
 */
internal fun pageTurnDistance(viewportHeight: Float, fraction: Float, overlap: Float): Float {
    if (!viewportHeight.isFinite() || viewportHeight <= 0f) return 0f

    val floor = viewportHeight * MIN_PAGE_FRACTION
    val cap = (viewportHeight - overlap.coerceAtLeast(0f)).coerceAtLeast(floor)
    return (viewportHeight * fraction).coerceIn(floor, cap)
}

/**
 * Turns pages for both of the reader's triggers — the tap zones and the
 * horizontal swipe — so that one action cannot behave two ways.
 *
 * [viewportHeight] is the height of the text area, which only the composable
 * that lays it out can know; it is written from there and read here.
 */
@Stable
class ReaderPager internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val step: () -> Float,
    private val overlap: () -> Float,
    private val animate: () -> Boolean
) {
    private val height = mutableFloatStateOf(0f)

    var viewportHeight: Float
        get() = height.floatValue
        set(value) {
            height.floatValue = value
        }

    /**
     * A turn asked for while one is still animating takes the scroll over from
     * it, so a second tap continues from wherever the first had got to rather
     * than adding a whole page to it. That is deliberate: one animation moves
     * one page, and a page and a half of text flying past is not a page turn.
     */
    fun turn(forward: Boolean) {
        val distance = pageTurnDistance(viewportHeight, step(), overlap())
        if (distance <= 0f) return

        val delta = if (forward) distance else -distance
        scope.launch {
            if (animate()) {
                listState.animateScrollBy(delta, tween(durationMillis = PAGE_TURN_DURATION_MS))
            } else {
                listState.scrollBy(delta)
            }
        }
    }
}

@Composable
fun rememberReaderPager(
    listState: LazyListState,
    stepFraction: Float,
    overlap: Dp,
    animate: Boolean
): ReaderPager {
    val scope = rememberCoroutineScope()
    val currentStep = rememberUpdatedState(stepFraction)
    val currentAnimate = rememberUpdatedState(animate)
    val overlapPx = with(LocalDensity.current) { overlap.toPx() }
    val currentOverlap = rememberUpdatedState(overlapPx)

    return remember(listState, scope) {
        ReaderPager(
            listState = listState,
            scope = scope,
            step = { currentStep.value },
            overlap = { currentOverlap.value },
            animate = { currentAnimate.value }
        )
    }
}
