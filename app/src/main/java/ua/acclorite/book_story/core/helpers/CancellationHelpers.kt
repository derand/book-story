/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import kotlin.coroutines.cancellation.CancellationException

/**
 * Rethrows a coroutine cancellation; does nothing for any other failure.
 *
 * [CancellationException] is an ordinary [Exception], so `catch (e: Exception)`
 * and [runCatching] both capture it — and a captured cancellation stops being
 * cancellation. The coroutine runs on to the end of the function, reports
 * whatever the failure branch reports, and the caller cannot tell "the user left"
 * from "this file is broken".
 *
 * Call this first in any catch that a cancellable operation can reach.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * [runCatching], except that a cancellation propagates instead of being captured.
 *
 * Prefer this over [runCatching] anywhere the block can suspend: a [Result] that
 * holds a [CancellationException] is a bug waiting for a caller to log it as an
 * error, retry it, or show it to the user.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        e.rethrowIfCancellation()
        Result.failure(e)
    }

/** [Result.mapCatching], with the same guarantee as [runCatchingCancellable]. */
inline fun <T, R> Result<T>.mapCatchingCancellable(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { value -> runCatchingCancellable { transform(value) } },
        onFailure = { failure -> Result.failure(failure) }
    )
