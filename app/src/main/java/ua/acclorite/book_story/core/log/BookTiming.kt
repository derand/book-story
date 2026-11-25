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
