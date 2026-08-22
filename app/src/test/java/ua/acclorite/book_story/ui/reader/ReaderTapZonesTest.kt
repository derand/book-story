/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shape of the reader's tap zones, which the gesture code only consults. */
class ReaderTapZonesTest {

    private val area = Size(1000f, 2000f)
    private val zones = ReaderTapZones()

    private fun ReaderTapZones.at(x: Float, y: Float = 1000f) =
        zoneAt(Offset(x, y), area)

    @Test
    fun theEdgesTurnPagesAndTheMiddleDoesNot() {
        assertEquals(ReaderTapZone.Previous, zones.at(x = 10f))
        assertEquals(ReaderTapZone.Center, zones.at(x = 500f))
        assertEquals(ReaderTapZone.Next, zones.at(x = 990f))
    }

    @Test
    fun theBoundariesBelongToTheMiddle() {
        // 30% of 1000 either side: [0, 300) turns back, [700, 1000] turns on.
        assertEquals(ReaderTapZone.Previous, zones.at(x = 299f))
        assertEquals(ReaderTapZone.Center, zones.at(x = 300f))
        assertEquals(ReaderTapZone.Center, zones.at(x = 699f))
        assertEquals(ReaderTapZone.Next, zones.at(x = 700f))
    }

    @Test
    fun theVerticalPositionSaysNothingAboutThisShape() {
        for (y in listOf(0f, 1f, 1999f, 5000f)) {
            assertEquals(ReaderTapZone.Previous, zones.at(x = 10f, y = y))
            assertEquals(ReaderTapZone.Next, zones.at(x = 990f, y = y))
        }
    }

    @Test
    fun invertingSwapsTheDirectionsAndNotTheShape() {
        val inverted = ReaderTapZones(inverted = true)

        assertEquals(ReaderTapZone.Next, inverted.at(x = 10f))
        assertEquals(ReaderTapZone.Center, inverted.at(x = 500f))
        assertEquals(ReaderTapZone.Previous, inverted.at(x = 990f))
    }

    @Test
    fun stripesCanBeAsymmetricAndCanBeAbsent() {
        val wideRight = ReaderTapZones(leftFraction = 0.1f, rightFraction = 0.4f)

        assertEquals(ReaderTapZone.Previous, wideRight.at(x = 99f))
        assertEquals(ReaderTapZone.Center, wideRight.at(x = 101f))
        assertEquals(ReaderTapZone.Next, wideRight.at(x = 601f))

        val forwardOnly = ReaderTapZones(leftFraction = 0f)
        assertEquals(ReaderTapZone.Center, forwardOnly.at(x = 0f))
        assertEquals(ReaderTapZone.Next, forwardOnly.at(x = 990f))
    }

    @Test
    fun theMiddleSurvivesAnyConfiguration() {
        // Whatever is asked for, the menu has to stay reachable.
        val greedy = ReaderTapZones(leftFraction = 0.9f, rightFraction = 0.9f)

        assertEquals(ReaderTapZone.Previous, greedy.at(x = 449f))
        assertEquals(ReaderTapZone.Center, greedy.at(x = 500f))
        assertEquals(ReaderTapZone.Next, greedy.at(x = 551f))
    }

    @Test
    fun anAreaOrAPositionThatIsNotThereTurnsNothing() {
        assertEquals(ReaderTapZone.Center, zones.zoneAt(Offset(10f, 10f), Size(0f, 0f)))
        assertEquals(ReaderTapZone.Center, zones.zoneAt(Offset(10f, 10f), Size.Unspecified))
        assertEquals(ReaderTapZone.Center, zones.zoneAt(Offset.Unspecified, area))
    }
}
