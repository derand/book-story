/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.settings

/**
 * A distance in lines, written for the reader rather than for the slider that
 * carries it.
 *
 * A `Slider` steps in whole numbers, so a setting measured in fractions of a
 * line counts [stepsPerLine] steps per line and reads the count back in lines:
 * `3` of a half-line grid is `1.5`, `3` of a quarter-line grid is `0.75`.
 *
 * A whole line keeps its bare form — `2` and not `2.0`, because a trailing zero
 * reads as precision that is not being offered.
 */
fun lineLabel(steps: Int, stepsPerLine: Int): String {
    val whole = steps / stepsPerLine
    val fraction = steps % stepsPerLine
    if (fraction == 0) return "$whole"

    val hundredths = fraction * 100 / stepsPerLine
    val decimals = hundredths.toString().padStart(2, '0').trimEnd('0')
    return "$whole.$decimals"
}
