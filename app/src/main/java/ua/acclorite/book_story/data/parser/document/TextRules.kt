/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

/**
 * Separator-like text: "---", "***", "___", also spaced out ("* * *"). Every
 * format keeps such a line as the literal text the author typed: it is never a
 * chapter title, and never turned into a drawn separator.
 */
internal val SEPARATOR_TEXT_REGEX = Regex("""^([-*_])(\s*\1){2,}$""")

/**
 * How many items of a phase run between two [kotlinx.coroutines.yield] calls.
 *
 * Yielding once per line cost a quarter of the whole parse (#39): the work
 * itself is microseconds and the round-trip through the dispatcher is ~166 µs,
 * moving the coroutine between worker threads each time. Cancellation is not
 * what paid for that — `ensureActive` still runs on every item and throws just
 * as promptly; a yield only lets *other* coroutines in, and at 64 items that is
 * still well under 50 ms of work between turns.
 */
internal const val YIELD_INTERVAL = 64
