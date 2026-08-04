/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The string half of finding a book by descending its path rather than walking the
 * whole SAF tree. The traversal itself needs a documents provider; this does not,
 * and it is where the mistakes live.
 */
class PathSegmentsUnderTest {

    @Test
    fun `nested path yields one segment per level`() {
        assertEquals(
            listOf("Books", "Lem", "solaris.fb2"),
            pathSegmentsUnder("/storage/emulated/0", "/storage/emulated/0/Books/Lem/solaris.fb2")
        )
    }

    @Test
    fun `file directly in the tree root yields one segment`() {
        assertEquals(
            listOf("solaris.fb2"),
            pathSegmentsUnder("/storage/emulated/0/Books", "/storage/emulated/0/Books/solaris.fb2")
        )
    }

    @Test
    fun `path outside the tree is refused`() {
        assertNull(pathSegmentsUnder("/storage/emulated/0/Books", "/storage/emulated/0/Docs/a.fb2"))
    }

    /** The boundary the old prefix check was missing. */
    @Test
    fun `a longer sibling directory is not a child`() {
        assertNull(
            pathSegmentsUnder("/storage/emulated/0/Books", "/storage/emulated/0/BooksOld/a.fb2")
        )
    }

    @Test
    fun `case differences in the root are ignored`() {
        assertEquals(
            listOf("a.fb2"),
            pathSegmentsUnder("/storage/emulated/0/books", "/storage/emulated/0/BOOKS/a.fb2")
        )
    }

    @Test
    fun `segment case is preserved for the listing to match ignoring it`() {
        assertEquals(
            listOf("Lem", "Solaris.FB2"),
            pathSegmentsUnder("/books", "/books/Lem/Solaris.FB2")
        )
    }

    @Test
    fun `trailing separators on either side are ignored`() {
        assertEquals(
            listOf("a.fb2"),
            pathSegmentsUnder("/books/", "/books/a.fb2/")
        )
    }

    @Test
    fun `the root itself names no file`() {
        assertNull(pathSegmentsUnder("/books", "/books"))
        assertNull(pathSegmentsUnder("/books", "/books/"))
    }

    @Test
    fun `an empty root is refused rather than claiming everything`() {
        assertNull(pathSegmentsUnder("", "/books/a.fb2"))
        assertNull(pathSegmentsUnder("   ", "/books/a.fb2"))
    }

    /** No listing composes a path with an empty segment, so there is nothing to find. */
    @Test
    fun `doubled separators are refused`() {
        assertNull(pathSegmentsUnder("/books", "/books//a.fb2"))
        assertNull(pathSegmentsUnder("/books", "/books/Lem//a.fb2"))
    }

    @Test
    fun `a path shorter than the root is refused`() {
        assertNull(pathSegmentsUnder("/storage/emulated/0/Books", "/storage"))
    }

    /**
     * Spaces and punctuation are ordinary in display names and must survive intact,
     * since the segment is compared to the name the cursor reports.
     */
    @Test
    fun `names keep their spaces and dots`() {
        assertEquals(
            listOf("Sci-Fi & Co.", "Vol. 1 (2005).fb2"),
            pathSegmentsUnder("/books", "/books/Sci-Fi & Co./Vol. 1 (2005).fb2")
        )
    }
}
