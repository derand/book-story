/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The search of a list screen: what is typed, and the [filter] the list is
 * narrowed to, which follows the typing once it pauses for [pauseMillis], or
 * at once on [submit]. Library, History and Browse each had their own copy of
 * this, a job per keystroke.
 */
class ListSearch(scope: CoroutineScope, pauseMillis: Long = 500) {

    private val typed = MutableStateFlow("")

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    init {
        scope.launch {
            typed.collectLatest { query ->
                delay(pauseMillis)
                _filter.value = query
            }
        }
    }

    fun type(query: String) {
        typed.value = query
    }

    /** The search key on the keyboard: no waiting for the pause. */
    fun submit() {
        _filter.value = typed.value
    }

    /** Search closed: the whole list again, at once. */
    fun clear() {
        typed.value = ""
        _filter.value = ""
    }
}
