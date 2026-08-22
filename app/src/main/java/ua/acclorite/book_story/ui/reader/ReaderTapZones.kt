/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified

/**
 * What a tap means once its position has been resolved against the reader's
 * text area. [Center] is "act on what is under the finger" — the menu, or the
 * image the finger landed on; the other two turn a page.
 */
enum class ReaderTapZone {
    Previous,
    Center,
    Next
}

/**
 * Where in the text area a tap turns a page, and where it does not.
 *
 * This is deliberately a layer of its own: the gesture code knows nothing about
 * geometry and the geometry knows nothing about gestures, so a different shape
 * — asymmetric halves, stripes that widen towards the thumb, boundaries from the
 * settings — replaces this one function without touching the plumbing. It takes
 * the whole position and the whole area for the same reason: a shape that also
 * depends on the vertical position needs no new signature.
 *
 * The shape shipped here is the familiar one: two vertical stripes along the
 * left and right edges, the rest of the width in the middle.
 *
 * @param leftFraction width of the left stripe, as a fraction of the area
 * @param rightFraction width of the right stripe, as a fraction of the area
 * @param inverted swaps which stripe goes back and which goes forward
 */
@Immutable
data class ReaderTapZones(
    val leftFraction: Float = DEFAULT_EDGE_FRACTION,
    val rightFraction: Float = DEFAULT_EDGE_FRACTION,
    val inverted: Boolean = false
) {

    fun zoneAt(position: Offset, area: Size): ReaderTapZone {
        if (area.isUnspecified || position.isUnspecified) return ReaderTapZone.Center
        if (!area.width.isFinite() || area.width <= 0f) return ReaderTapZone.Center
        if (!position.x.isFinite()) return ReaderTapZone.Center

        val left = area.width * leftFraction.coerceIn(0f, MAX_EDGE_FRACTION)
        val right = area.width - area.width * rightFraction.coerceIn(0f, MAX_EDGE_FRACTION)

        return when {
            position.x < left -> if (inverted) ReaderTapZone.Next else ReaderTapZone.Previous
            position.x >= right -> if (inverted) ReaderTapZone.Previous else ReaderTapZone.Next
            else -> ReaderTapZone.Center
        }
    }

    companion object {
        /** Wide enough to hit without aiming, narrow enough to leave the page readable. */
        const val DEFAULT_EDGE_FRACTION = 0.3f

        /** Past this the middle would vanish and the menu would become unreachable. */
        const val MAX_EDGE_FRACTION = 0.45f
    }
}
