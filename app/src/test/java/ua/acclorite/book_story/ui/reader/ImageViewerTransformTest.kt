/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit-style checks for the full-screen image viewer's zoom/pan geometry. */
class ImageViewerTransformTest {

    private val container = Size(1000f, 2000f)
    private val center = Offset(500f, 1000f)

    @Test
    fun aWideImageFitsToTheViewerWidth() {
        // 2:1 in a 1:2 viewer — the width runs out first.
        val fitted = fittedSize(container, aspectRatio = 2f)

        assertEquals(1000f, fitted.width, TOLERANCE)
        assertEquals(500f, fitted.height, TOLERANCE)
    }

    @Test
    fun aTallImageFitsToTheViewerHeight() {
        val fitted = fittedSize(container, aspectRatio = 0.25f)

        assertEquals(2000f, fitted.height, TOLERANCE)
        assertEquals(500f, fitted.width, TOLERANCE)
    }

    @Test
    fun anUnknownContainerOrRatioHasNoFittedSize() {
        assertEquals(Size.Zero, fittedSize(Size.Zero, aspectRatio = 1.5f))
        assertEquals(Size.Zero, fittedSize(container, aspectRatio = 0f))
        assertEquals(Size.Zero, fittedSize(container, aspectRatio = Float.NaN))
    }

    @Test
    fun oneToOneIsAboveFitForAnImageThatOutResolvesTheScreen() {
        // A 3000 px wide manga page drawn 1000 px wide.
        val scale = oneToOneScale(imageWidth = 3000, fitted = Size(1000f, 1400f))

        assertEquals(3f, scale, TOLERANCE)
        assertEquals(FIT_SCALE, minScaleFor(scale), TOLERANCE)
    }

    @Test
    fun oneToOneIsBelowFitForASmallScanAndBecomesTheZoomFloor() {
        // A 250 px scan blown up to 1000 px: only zooming out shows real pixels.
        val scale = oneToOneScale(imageWidth = 250, fitted = Size(1000f, 1000f))

        assertEquals(0.25f, scale, TOLERANCE)
        assertEquals(0.25f, minScaleFor(scale), TOLERANCE)
    }

    @Test
    fun theZoomCeilingLeavesRoomBeyondOneToOne() {
        // A modest image still gets a usable magnifier...
        assertTrue(maxScaleFor(oneToOne = 1.5f) > 1.5f)
        // ...and a huge one may be taken all the way to its own pixels.
        assertEquals(8f, maxScaleFor(oneToOne = 8f), TOLERANCE)
    }

    @Test
    fun doubleTapGoesToOneToOneAndBack() {
        assertEquals(3f, doubleTapScale(current = FIT_SCALE, oneToOne = 3f), TOLERANCE)
        assertEquals(FIT_SCALE, doubleTapScale(current = 3f, oneToOne = 3f), TOLERANCE)
        // Zooming out to real pixels is just as valid a target.
        assertEquals(0.25f, doubleTapScale(current = FIT_SCALE, oneToOne = 0.25f), TOLERANCE)
    }

    @Test
    fun doubleTapFallsBackToPlainMagnificationWhenOneToOneWouldDoNothing() {
        val target = doubleTapScale(current = FIT_SCALE, oneToOne = 1.001f)

        assertTrue("expected a real zoom, got $target", target > 1.5f)
    }

    @Test
    fun anImageThatDoesNotFillTheViewerIsPinnedToTheCentre() {
        val fitted = Size(1000f, 500f)
        val clamped = clampOffset(
            offset = Offset(300f, -400f),
            scale = FIT_SCALE,
            fitted = fitted,
            container = container
        )

        assertEquals(0f, clamped.x, TOLERANCE)
        assertEquals(0f, clamped.y, TOLERANCE)
    }

    @Test
    fun panStopsAtTheImageEdge() {
        val fitted = Size(1000f, 2000f)
        // At 2x the image is 2000x4000 in a 1000x2000 viewer: 500/1000 of slack.
        val clamped = clampOffset(
            offset = Offset(900f, -1800f),
            scale = 2f,
            fitted = fitted,
            container = container
        )

        assertEquals(500f, clamped.x, TOLERANCE)
        assertEquals(-1000f, clamped.y, TOLERANCE)
    }

    @Test
    fun pinchingKeepsTheContentUnderTheFingersInPlace() {
        val fitted = Size(1000f, 2000f)
        val anchor = Offset(250f, 500f)

        val zoomed = ViewerTransform().transformedBy(
            anchor = anchor,
            zoom = 2f,
            pan = Offset.Zero,
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        assertEquals(2f, zoomed.scale, TOLERANCE)
        // At fit the content point under the anchor is the anchor itself; after
        // the pinch it must still be drawn there.
        val stillThere = zoomed.screenPositionOf(anchor)
        assertEquals(anchor.x, stillThere.x, TOLERANCE)
        assertEquals(anchor.y, stillThere.y, TOLERANCE)
    }

    @Test
    fun zoomingBackOutRecentresTheImage() {
        val fitted = Size(1000f, 2000f)
        val zoomed = ViewerTransform().transformedBy(
            anchor = Offset(100f, 100f),
            zoom = 3f,
            pan = Offset.Zero,
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        val restored = zoomed.transformedBy(
            anchor = Offset(100f, 100f),
            zoom = FIT_SCALE / zoomed.scale,
            pan = Offset.Zero,
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        assertEquals(FIT_SCALE, restored.scale, TOLERANCE)
        // Nothing to pan at fit, so the clamp has put it back dead centre.
        assertEquals(0f, restored.offset.x, TOLERANCE)
        assertEquals(0f, restored.offset.y, TOLERANCE)
    }

    @Test
    fun theZoomCeilingAndFloorAreRespected() {
        val fitted = Size(1000f, 2000f)

        val tooFar = ViewerTransform().transformedBy(
            anchor = center,
            zoom = 100f,
            pan = Offset.Zero,
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )
        assertEquals(5f, tooFar.scale, TOLERANCE)

        val tooClose = tooFar.transformedBy(
            anchor = center,
            zoom = 0.001f,
            pan = Offset.Zero,
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )
        assertEquals(FIT_SCALE, tooClose.scale, TOLERANCE)
    }

    @Test
    fun aGestureCannotStrandTheImageOffScreen() {
        val fitted = Size(1000f, 2000f)

        val dragged = ViewerTransform(scale = 2f).transformedBy(
            anchor = center,
            zoom = 1f,
            pan = Offset(5000f, 5000f),
            fitted = fitted,
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        assertEquals(500f, dragged.offset.x, TOLERANCE)
        assertEquals(1000f, dragged.offset.y, TOLERANCE)
    }

    @Test
    fun aTransformFromAnotherScreenSizeIsPulledBackIntoBounds() {
        // Zoomed in and panned to the far corner of a portrait viewer...
        val strandedByRotation = ViewerTransform(scale = 3f, offset = Offset(1000f, 2000f))

        // ...then rotated. The activity handles the config change itself, so this
        // exact transform survives into a landscape viewer it does not fit.
        val landscape = Size(2000f, 1000f)
        val fitted = fittedSize(landscape, aspectRatio = 0.5f)
        val reclamped = strandedByRotation.transformedBy(
            anchor = Offset(landscape.width / 2f, landscape.height / 2f),
            zoom = 1f,
            pan = Offset.Zero,
            fitted = fitted,
            container = landscape,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        assertEquals(3f, reclamped.scale, TOLERANCE)
        // 500x1000 fitted at 3x is 1500x3000: narrower than the viewer, so the
        // image is pinned horizontally and can only travel 1000 vertically.
        assertEquals(0f, reclamped.offset.x, TOLERANCE)
        assertEquals(1000f, reclamped.offset.y, TOLERANCE)
    }

    @Test
    fun aScaleAboveTheNewCeilingIsPulledDownToIt() {
        // A 1:1 that a small screen allowed, re-measured against a bigger one
        // whose fitted size makes the same image far less magnifiable.
        val stale = ViewerTransform(scale = 8f)

        val reclamped = stale.transformedBy(
            anchor = center,
            zoom = 1f,
            pan = Offset.Zero,
            fitted = Size(1000f, 2000f),
            container = container,
            minScale = FIT_SCALE,
            maxScale = 5f
        )

        assertEquals(5f, reclamped.scale, TOLERANCE)
    }

    @Test
    fun anUnmeasuredViewerLeavesTheTransformAlone() {
        val transform = ViewerTransform(scale = 2f, offset = Offset(10f, 10f))

        assertEquals(
            transform,
            transform.transformedBy(
                anchor = Offset.Zero,
                zoom = 2f,
                pan = Offset.Zero,
                fitted = Size.Zero,
                container = Size.Zero,
                minScale = FIT_SCALE,
                maxScale = 5f
            )
        )
    }

    @Test
    fun aVerticalDragIsFreeToDismissOnlyWhileTheImageFitsTheHeight() {
        val fitted = Size(1000f, 1000f)

        assertFalse(canPanVertically(FIT_SCALE, fitted, container))
        assertFalse(canPanVertically(2f, fitted, container))
        assertTrue(canPanVertically(2.5f, fitted, container))
    }

    /** Where the content point [point] (in fitted coordinates) ends up drawn. */
    private fun ViewerTransform.screenPositionOf(point: Offset): Offset =
        center + (point - center) * scale + offset

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
