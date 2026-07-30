/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

/**
 * How many encoded image bytes one load pass may keep in memory before spilling
 * the rest to files.
 *
 * It exists so that a book whose images are small — a cover and a few
 * illustrations, which is most books — never writes a session file at all. The
 * alternative would be writing tens of kilobytes to disk only to delete them
 * again when the reader closes.
 *
 * The ceiling is a **constant**, deliberately not "whatever memory is free": how
 * much is free is not a stable property (Android reclaims under pressure), so
 * treating it as one would mean keeping the file path anyway *and* growing a
 * spill-to-disk transition between the two. A fixed budget needs neither, because
 * it is small enough to simply hold. The heap-relative term only keeps it modest
 * on a device whose heap is small to begin with.
 *
 * One instance per pass: the tally is the open book's, and it dies with it.
 */
class ImageMemoryBudget(private val maxBytes: Long = defaultMaxBytes()) {

    private var claimed = 0L

    /**
     * Claims room for [size] bytes, reporting whether it was there. An image too
     * big to fit does not close the budget: a smaller one after it still can.
     *
     * Which images end up in memory is first-come: the book's own order when they
     * come from a scan of it, the order a fresh parse hands them over in
     * otherwise. That is left unspecified on purpose — it only decides *which*
     * half of a book too big to fit is on disk, and a book that fits, fits
     * whole.
     */
    fun claim(size: Int): Boolean {
        if (claimed + size > maxBytes) return false
        claimed += size
        return true
    }

    private companion object {

        /** Enough for an ordinary illustrated book, and never a large share of the heap. */
        const val CEILING_BYTES = 8L * 1024 * 1024

        /**
         * Coil's own cache of decoded bitmaps already takes ~25 % of the heap, so
         * this stays an order of magnitude below that.
         */
        fun defaultMaxBytes(): Long =
            minOf(CEILING_BYTES, Runtime.getRuntime().maxMemory() / 16)
    }
}
