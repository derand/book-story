/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real time, with a short pause: the gaps are wide enough not to race. */
class ListSearchTest {

    private fun test(block: suspend CoroutineScope.(ListSearch) -> Unit) = runBlocking {
        block(ListSearch(this, pauseMillis = PAUSE))
        coroutineContext.cancelChildren()
    }

    @Test
    fun `the filter follows the typing only once it pauses`() = test { search ->
        delay(PAUSE * 3) // the initial blank has settled
        search.type("du")
        delay(PAUSE / 4)
        search.type("dune")
        assertEquals("", search.filter.value)

        withTimeout(PAUSE * 10) { search.filter.first { it == "dune" } }
    }

    @Test
    fun `submit applies the query at once`() = test { search ->
        search.type("dune")
        search.submit()
        assertEquals("dune", search.filter.value)
    }

    @Test
    fun `clear empties the filter at once and drops a pending query`() = test { search ->
        search.type("dune")
        search.submit()
        search.type("emma")
        search.clear()
        assertEquals("", search.filter.value)

        delay(PAUSE * 3)
        assertEquals("", search.filter.value)
    }

    private companion object {
        const val PAUSE = 100L
    }
}
