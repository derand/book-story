/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * How much blank space marks the boundary between two chapters.
 *
 * Typography has one rule here: **the space above a title must exceed the space
 * below it**, because that is what makes the title belong to the text it opens
 * rather than to the text that ended. So the break is measured out here, and
 * nothing is drawn under the title at all — a rule there separates a title from
 * its own first paragraph, which is the one place with nothing to separate.
 *
 * The space belongs to the *end of the chapter that is finishing*, not to the
 * top of the one that begins, and that placement is doing real work:
 *
 * - The first chapter of a book has nothing before it, so it needs no special
 *   case to go without a break.
 * - `OnScrollToChapter` calls `requestScrollToItem(index, scrollOffset = 0)`,
 *   which puts the *item's* top at the top of the viewport. Space living above
 *   the title is inside that item, so a jump to a chapter would arrive on a
 *   screenful of nothing with the title pushed below it.
 *
 * Measured in lines rather than in `dp` or in paragraph gaps. A `dp` does not
 * follow the type — the distance would shrink to nothing as the font grows. A
 * paragraph gap does follow it, but the reader is allowed to set that gap to
 * zero, and a chapter break that a *text* setting can switch off is not one.
 *
 * How many lines is the reader's own to say. The setting names one number — the
 * break before a top-level chapter — and the ladder below is expressed as
 * divisors of it rather than as a second list of sizes, so a hierarchy that was
 * agreed once is not something a slider can flatten. At the default of a line
 * and a half the ladder is 1.5, 1, 0.75 and 0.5; at three lines it is 3, 2, 1.5
 * and 1, which is exactly what the boundary was before the setting existed.
 */
private fun depthDivisor(depth: Int): Float = when (depth) {
    0 -> 1f
    1 -> 1.5f
    2 -> 2f
    else -> 3f
}

/**
 * The break to leave below the entry at [index], or zero when no chapter starts
 * after it — [breakLines] lines of [lineHeight] before a top-level chapter, and
 * a fraction of that deeper down.
 *
 * It reads ahead rather than taking the immediate successor because an [images]
 * setting of `false` drops every [ReaderText.Image] from the list without
 * rendering anything at all — so a chapter that follows the illustration
 * closing the previous one would otherwise lose its break along with the
 * picture.
 *
 * The depth consulted is the *incoming* chapter's, the same signal the title's
 * own size already reads: leaving a sub-subsection for a new top-level chapter
 * is the strongest break in a book, and it is strong because of what begins,
 * not because of what ended.
 */
fun chapterBreakAfter(
    text: List<ReaderText>,
    index: Int,
    images: Boolean,
    lineHeight: Dp,
    breakLines: Float
): Dp {
    // Asked for no break, and this is asked once per visible entry.
    if (breakLines <= 0f) return 0.dp

    for (next in index + 1..text.lastIndex) {
        when (val entry = text[next]) {
            is ReaderText.Chapter ->
                return lineHeight * (breakLines / depthDivisor(entry.depth))

            is ReaderText.Image -> if (images) return 0.dp
            else -> return 0.dp
        }
    }

    return 0.dp
}
