/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageCodecTest {

    @Test
    fun `nothing read encodes to nothing`() {
        assertEquals("", CoverageCodec.encode(emptySet()))
        assertEquals(emptySet<Int>(), CoverageCodec.decode(""))
    }

    @Test
    fun `a run becomes one range`() {
        assertEquals("3-7", CoverageCodec.encode(setOf(3, 4, 5, 6, 7)))
    }

    @Test
    fun `a lone index is written without a redundant range`() {
        assertEquals("12", CoverageCodec.encode(setOf(12)))
    }

    @Test
    fun `gaps split the runs, in order`() {
        // The set arrives unordered — reading jumps around.
        val covered = setOf(3900, 13, 12, 5200, 14, 3901)

        assertEquals("12-14,3900-3901,5200", CoverageCodec.encode(covered))
    }

    @Test
    fun `round trip keeps exactly what was covered`() {
        val covered = buildSet {
            addAll(0..40)
            addAll(500..502)
            add(9999)
        }

        assertEquals(covered, CoverageCodec.decode(CoverageCodec.encode(covered)))
    }

    @Test
    fun `reads a single-index range written the long way`() {
        assertEquals(setOf(12), CoverageCodec.decode("12-12"))
    }

    @Test
    fun `a damaged piece costs only itself`() {
        // The string has been through a database; losing one range must not
        // lose the reading history of the whole book.
        assertEquals(setOf(1, 2, 3, 90), CoverageCodec.decode("1-3,oops,90"))
    }

    @Test
    fun `a backwards range is discarded rather than guessed at`() {
        assertEquals(setOf(90), CoverageCodec.decode("40-3,90"))
    }

    @Test
    fun `tolerates stray whitespace and empty parts`() {
        assertEquals(setOf(1, 2, 5), CoverageCodec.decode(" 1-2 , ,5 "))
    }
}
