/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import ua.acclorite.book_story.core.helpers.coerceAndPreventNaN

/**
 * How far through the book a screen is, from 0 to 1.
 *
 * The first item reads as 0 and a screen that shows the last item reads as 1,
 * so a short last chapter can still be finished. Anything else is the first
 * visible item's share of the text.
 *
 * [lastVisibleIndex] is null when nothing is laid out on screen — a zero-height
 * viewport, such as split screen dragged to its minimum. The first visible
 * index alone decides then.
 */
fun readingProgress(firstVisibleIndex: Int, lastVisibleIndex: Int?, lastIndex: Int): Float {
    if (firstVisibleIndex == 0) return 0f
    if (lastVisibleIndex != null && lastVisibleIndex >= lastIndex) return 1f

    return firstVisibleIndex
        .div(lastIndex.toFloat())
        .coerceAndPreventNaN()
}
