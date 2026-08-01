/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.log

/**
 * One timeline for opening a book, measured from the tap that started it.
 *
 * [timed] answers "how long did this step take"; this answers "how long until
 * this step happened", which is the only way to see the parts of an open that
 * are nobody's `timed` block — navigation, the DB read, composition, and the
 * gap between the text existing and the text being on screen.
 *
 * A trace runs from [start] (a tap on a book) to [end] (the first frame that
 * carries text). Outside one, [mark] does nothing: entering the reader by some
 * other route — a restored back stack, say — has no tap to measure from, and a
 * number without an origin is worse than no number.
 *
 * Costs nothing when [BOOK_TIMING] is off, and single-user by construction:
 * there is one reader, opened by one tap at a time.
 */
object BookOpenTrace {

    @Volatile
    private var startNs: Long = 0

    /** Starts a trace — call at the tap, before navigating. */
    fun start(what: String) {
        if (!BOOK_TIMING) return
        startNs = System.nanoTime()
        logI(TAG, "open $what — t0")
    }

    /** Notes that [label] has been reached, if a trace is running. */
    fun mark(label: String) {
        if (!BOOK_TIMING) return
        val start = startNs
        if (start == 0L) return
        logI(TAG, format(start, label))
    }

    /** [mark]s [label] and closes the trace, so nothing later appends to it. */
    fun end(label: String) {
        if (!BOOK_TIMING) return
        val start = startNs
        if (start == 0L) return
        startNs = 0
        logI(TAG, format(start, label))
    }

    private fun format(start: Long, label: String): String {
        val ms = (System.nanoTime() - start) / 1_000_000
        return "  +${ms.toString().padStart(5)} ms  $label"
    }
}
