/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.log

import ua.acclorite.book_story.BuildConfig

@PublishedApi
internal const val TAG = "BookTiming"

/**
 * Whether opening a book is timed into the log — read with
 * `adb logcat -s BookTiming`.
 *
 * On for `debug` and `release-debug`, off for `release`. Deliberately **not**
 * `BuildConfig.DEBUG`: `release-debug` is a release build type, and it is the
 * variant worth measuring, since a debug build runs several times slower and its
 * numbers mean nothing on their own.
 */
const val BOOK_TIMING = BuildConfig.BOOK_TIMING

/**
 * Runs [block], logging how long it took and whatever [describe] says about the
 * result. Costs nothing when [BOOK_TIMING] is off: no clock is read, and the line
 * is never built.
 *
 * Keep [label] a constant — a interpolated one is built either way.
 */
inline fun <T> timed(
    label: String,
    crossinline describe: (T) -> String = { "" },
    block: () -> T
): T {
    if (!BOOK_TIMING) return block()

    val start = System.nanoTime()
    val result = block()
    val ms = (System.nanoTime() - start) / 1_000_000

    logI(TAG, "$label: $ms ms ${describe(result)}".trimEnd())
    return result
}

/** Notes something worth reading next to the timings, e.g. a cache hit. */
inline fun bookTimingNote(note: () -> String) {
    if (BOOK_TIMING) logI(TAG, note())
}

/**
 * A [timed] for work that happens a great many times: it adds up instead of
 * logging, so a step running once per line of the book yields one line of log
 * rather than one per line of book.
 *
 * Not reentrant, and not safe across threads — a sum belongs to one parse, and
 * a parse is one coroutine. Costs nothing when [BOOK_TIMING] is off beyond the
 * call itself, which is why [add] takes a plain lambda: at 2.9M characters the
 * allocation is noise next to what is being measured, and inlining it would put
 * the counters in the hot path of every build.
 */
class TimingSum(private val label: String) {

    private var ns = 0L
    private var calls = 0

    fun <T> add(block: () -> T): T {
        if (!BOOK_TIMING) return block()

        val start = System.nanoTime()
        try {
            return block()
        } finally {
            ns += System.nanoTime() - start
            calls++
        }
    }

    fun reset() {
        ns = 0
        calls = 0
    }

    /** Logs the total, or nothing at all if the step never ran. */
    fun log(indent: String = "      ") {
        if (!BOOK_TIMING || calls == 0) return
        logI(TAG, "$indent$label: ${ns / 1_000_000} ms, $calls calls")
    }
}
