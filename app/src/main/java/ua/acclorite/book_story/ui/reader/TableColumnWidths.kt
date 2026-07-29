/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

/**
 * Turns the per-column natural and minimum widths of a table into the actual
 * column widths, following the simplified HTML "auto" table algorithm.
 *
 * - Everything fits: use the natural widths and hand the leftover space out in
 *   proportion to them, so the table still fills [available].
 * - Too wide: keep every column at its minimum and share what is left over in
 *   proportion to each column's excess (`natural - minimum`). A column already
 *   at its minimum is not squeezed further to make room for a greedier one.
 * - Even the minimums do not fit: fall back to sharing [available] in
 *   proportion to the minimums; some cells will clip, but nothing overflows.
 *
 * All values are pixels and include the cell padding. The result always sums to
 * exactly [available], so the table matches the page width to the pixel.
 *
 * @param natural per-column width needed to lay every cell out on one line.
 * @param minimum per-column width of the widest unbreakable run; must not
 * exceed the matching [natural] entry.
 */
internal fun resolveColumnWidths(
    natural: IntArray,
    minimum: IntArray,
    available: Int
): IntArray {
    require(natural.size == minimum.size) { "natural and minimum must be the same size" }
    if (natural.isEmpty() || available <= 0) return IntArray(natural.size)

    val naturalTotal = natural.sumOf { it.toLong() }
    val minimumTotal = minimum.sumOf { it.toLong() }

    val base: IntArray
    val weights: IntArray
    when {
        naturalTotal <= available -> {
            base = natural
            weights = natural
        }

        minimumTotal < available -> {
            base = minimum
            weights = IntArray(natural.size) { natural[it] - minimum[it] }
        }

        else -> {
            base = IntArray(natural.size)
            weights = minimum
        }
    }

    val spare = (available - base.sumOf { it.toLong() }).coerceAtLeast(0L).toInt()
    val extra = distribute(spare, weights)
    return IntArray(base.size) { base[it] + extra[it] }
}

/**
 * Splits [total] over [weights] so that the parts sum to exactly [total]:
 * every column gets its floored share, and the largest remainders get the
 * leftover pixels. Zero total weight means an even split.
 */
private fun distribute(total: Int, weights: IntArray): IntArray {
    val parts = IntArray(weights.size)
    if (total <= 0) return parts

    val weightTotal = weights.sumOf { it.toLong() }
    var handedOut = 0L
    val remainders = LongArray(weights.size)
    for (index in weights.indices) {
        val exact = if (weightTotal > 0L) {
            total.toLong() * weights[index]
        } else {
            total.toLong()
        }
        val divisor = if (weightTotal > 0L) weightTotal else weights.size.toLong()
        parts[index] = (exact / divisor).toInt()
        remainders[index] = exact % divisor
        handedOut += parts[index]
    }

    // Hand the rounding leftovers to the columns that lost the most to flooring.
    var leftover = total - handedOut
    while (leftover > 0) {
        var best = -1
        for (index in remainders.indices) {
            if (remainders[index] > 0 && (best < 0 || remainders[index] > remainders[best])) {
                best = index
            }
        }
        if (best < 0) best = 0 else remainders[best] = 0
        parts[best]++
        leftover--
    }
    return parts
}
