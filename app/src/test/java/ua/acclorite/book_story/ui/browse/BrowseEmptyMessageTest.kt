/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.R

/**
 * Which of the two empty states Browse is in. The screen used to have one
 * message for both, so a user whose folders had all gone quiet was invited to
 * add some.
 */
class BrowseEmptyMessageTest {

    @Test
    fun `no folder granted is an invitation`() {
        assertEquals(
            R.string.browse_empty,
            browseEmptyMessage(sourcesGranted = 0, sourcesAvailable = 0)
        )
    }

    @Test
    fun `folders granted and none answering is a fault`() {
        assertEquals(
            R.string.browse_sources_unavailable,
            browseEmptyMessage(sourcesGranted = 3, sourcesAvailable = 0)
        )
    }

    /** One source answering is enough: the folders it holds are simply empty. */
    @Test
    fun `one source answering is an invitation again`() {
        assertEquals(
            R.string.browse_empty,
            browseEmptyMessage(sourcesGranted = 3, sourcesAvailable = 1)
        )
    }

    @Test
    fun `every source answering is an invitation`() {
        assertEquals(
            R.string.browse_empty,
            browseEmptyMessage(sourcesGranted = 2, sourcesAvailable = 2)
        )
    }
}
