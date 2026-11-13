/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs [block] while anything collects [state], cancels it when the last
 * collector leaves, and runs it again from the start when one returns.
 *
 * For a model that outlives its screen: the Library and History models live as
 * long as the activity, but their screens are composed only while shown, so the
 * database is watched only while someone can see the result.
 */
fun CoroutineScope.launchWhileSubscribed(
    state: MutableStateFlow<*>,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    state.subscriptionCount
        .map { it > 0 }
        .distinctUntilChanged()
        .collectLatest { subscribed ->
            if (subscribed) coroutineScope { block() }
        }
}
