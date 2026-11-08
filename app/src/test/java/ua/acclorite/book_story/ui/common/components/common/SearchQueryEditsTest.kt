/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryEditsTest {

    /** Emits [values] the way a field does: the opening value first, then edits apart in time. */
    private fun field(vararg values: String) = flow {
        values.forEachIndexed { index, value ->
            if (index > 0) delay(60)
            emit(value)
        }
    }

    @Test
    fun `erasing back to the opening value is an edit`() = runBlocking {
        val edits = field("", "QA", "").queryEdits(debounceMillis = 10).toList()

        assertEquals(listOf("QA", ""), edits)
    }

    @Test
    fun `the opening value alone sends nothing`() = runBlocking {
        val edits = field("saved query").queryEdits(debounceMillis = 10).toList()

        assertEquals(emptyList<String>(), edits)
    }

    @Test
    fun `fast typing sends only where it settles`() = runBlocking {
        val edits = flow {
            emit("")
            delay(60)
            emit("Q")
            emit("QA")
        }.queryEdits(debounceMillis = 30).toList()

        assertEquals(listOf("QA"), edits)
    }
}
