/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.reader.SearchMatch

/**
 * The reader's search, which is a *mode* rather than a bar: it outlives hiding
 * the menu, so reading a found passage with the bars away does not end it. Only
 * closing it does — either by going back to reading or by staying put.
 */
@Immutable
data class ReaderSearch(
    val active: Boolean = false,
    val query: String = "",
    /** A scan is running: the count is not known yet, which is not the same as zero. */
    val scanning: Boolean = false,
    val matches: List<SearchMatch> = emptyList(),
    /** Index into [matches] of the one jumped to; -1 until an arrow is pressed. */
    val current: Int = -1,
    /**
     * Where reading was when the search opened. This is what "back to reading"
     * restores, and it is the reason searching cannot lose a reader's place.
     */
    val origin: Checkpoint? = null
) {
    val currentMatch: SearchMatch?
        get() = matches.getOrNull(current)
}

/**
 * What the counter says. The reader is either *on* a match or *between* two of
 * them — and before the first jump it is usually between, which no single
 * number can express.
 */
@Immutable
sealed interface SearchPosition {
    /** Nothing to count: no query, or no occurrence of it. */
    data object None : SearchPosition

    /** On the [ordinal]-th match (1-based). */
    data class At(val ordinal: Int) : SearchPosition

    /** Between the [before]-th match and the next one; [before] may be 0. */
    data class Between(val before: Int) : SearchPosition
}

/**
 * Where the reader stands among [matches], given the items [visible] on screen.
 *
 * A match that is already on screen is reported by its ordinal even before any
 * jump: it is in front of the reader, so saying "between 34 and 35" would be
 * pedantry.
 */
fun searchPosition(
    matches: List<SearchMatch>,
    current: Int,
    visible: IntRange
): SearchPosition {
    if (matches.isEmpty()) return SearchPosition.None
    matches.getOrNull(current)?.let { return SearchPosition.At(current + 1) }

    val onScreen = matches.indexOfFirst { it.itemIndex in visible }
    if (onScreen != -1) return SearchPosition.At(onScreen + 1)

    return SearchPosition.Between(matches.count { it.itemIndex < visible.first })
}

/**
 * The match an arrow lands on, or null when there is none in that direction.
 *
 * Stepping from a match is per *match*, so two occurrences in one paragraph are
 * two stops. Entering from reading is per *screen*: the arrows leave what is
 * already in front of the reader and go to the closest match beyond it.
 */
fun ReaderSearch.stepTarget(forward: Boolean, visible: IntRange): Int? {
    if (matches.isEmpty()) return null

    if (matches.getOrNull(current) != null) {
        val next = if (forward) current + 1 else current - 1
        return next.takeIf { it in matches.indices }
    }

    val target = when (forward) {
        true -> matches.indexOfFirst { it.itemIndex > visible.last }
        false -> matches.indexOfLast { it.itemIndex < visible.first }
    }
    return target.takeIf { it != -1 }
}
