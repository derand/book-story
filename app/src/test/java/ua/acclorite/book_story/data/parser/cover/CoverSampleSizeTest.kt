/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a cover is subsampled on its way into the app. The decode itself needs
 * a real `BitmapFactory`; the arithmetic that decides how much of the image is
 * read does not, and it is the whole of the fix.
 */
class CoverSampleSizeTest {

    @Test
    fun `an image at the target is read whole`() {
        assertEquals(1, coverSampleSize(1024, 1024))
        assertEquals(1, coverSampleSize(680, 1024))
    }

    /** Nothing is ever enlarged: a small cover stays the small cover it is. */
    @Test
    fun `a small image is read whole`() {
        assertEquals(1, coverSampleSize(120, 180))
        assertEquals(1, coverSampleSize(1, 1))
    }

    @Test
    fun `twice the target halves once`() {
        assertEquals(2, coverSampleSize(2048, 2048))
        assertEquals(2, coverSampleSize(1400, 2100))
    }

    /** The cover out of the issue: 6000×6000, ~144 MB decoded whole. */
    @Test
    fun `a huge cover is quartered, not decoded whole`() {
        assertEquals(4, coverSampleSize(6000, 6000))
        assertEquals(16, coverSampleSize(30000, 30000))
    }

    @Test
    fun `the longest side decides`() {
        assertEquals(coverSampleSize(4000, 500), coverSampleSize(500, 4000))
        assertEquals(2, coverSampleSize(2048, 10))
    }

    /**
     * The contract the caller relies on, over every size a cover can be: what
     * comes back is never smaller than the target, and never more than one
     * halving away from it — `inSampleSize` can only halve, so overshooting is
     * the only direction available.
     */
    @Test
    fun `the result stays between the target and twice it`() {
        var side = 1
        while (side <= 40000) {
            val sample = coverSampleSize(side, side)
            val decoded = side / sample

            assertTrue("$side sampled by $sample is larger than it was", sample >= 1)
            if (side >= COVER_TARGET_PX) {
                assertTrue("$side sampled to $decoded, below the target", decoded >= COVER_TARGET_PX)
                assertTrue(
                    "$side sampled to $decoded, more than a halving above the target",
                    decoded < COVER_TARGET_PX * 2
                )
            } else {
                assertEquals(1, sample)
            }
            side += 7
        }
    }

    /** Bounds a decoder reports for something that is not an image. */
    @Test
    fun `degenerate bounds ask for no sampling`() {
        assertEquals(1, coverSampleSize(0, 0))
        assertEquals(1, coverSampleSize(-1, 1024))
        assertEquals(1, coverSampleSize(4000, 4000, target = 0))
    }

    @Test
    fun `a smaller target samples further down`() {
        assertEquals(8, coverSampleSize(6000, 6000, target = 512))
        assertEquals(2, coverSampleSize(6000, 6000, target = 2048))
    }
}
