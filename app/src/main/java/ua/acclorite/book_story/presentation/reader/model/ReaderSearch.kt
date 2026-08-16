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
 * Derived from [searchPosition] rather than from the screen, so the arrows can
 * never disagree with the counter: whatever "34 / 57" claims is where the next
 * step counts from. With five matches on one screen the counter names the first
 * of them, and "next" is the second — not the first one past the screen.
 *
 * Stepping is therefore always per *match*, which also makes two occurrences in
 * one paragraph two stops.
 */
fun ReaderSearch.stepTarget(forward: Boolean, visible: IntRange): Int? {
    val next = when (val position = searchPosition(matches, current, visible)) {
        SearchPosition.None -> return null

        // On a match: one along from it.
        is SearchPosition.At -> when (forward) {
            true -> position.ordinal
            false -> position.ordinal - 2
        }

        // Between two of them: [before] matches are behind, so that ordinal is
        // the closest one ahead — and one less is the closest one behind.
        is SearchPosition.Between -> when (forward) {
            true -> position.before
            false -> position.before - 1
        }
    }
    return next.takeIf { it in matches.indices }
}
