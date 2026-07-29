/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * The scale at which the image exactly fits the viewer — the state it opens in.
 * Everything else is expressed relative to it, so the viewer never needs to know
 * how big the image is drawn.
 */
internal const val FIT_SCALE = 1f

/**
 * Zoom ceiling when the image does not out-resolve the screen by more than this.
 * A screen pixel is far smaller than what an eye resolves, so stopping exactly at
 * the image's own pixels would leave small print unreadable on a dense display.
 */
private const val MIN_MAX_SCALE = 5f

/** Fallback magnification for a double tap that has no 1:1 state to go to. */
private const val PLAIN_ZOOM_SCALE = 2f

/** Below this a scale change is not worth animating to — it looks like nothing happened. */
private const val SCALE_EPSILON = 0.01f

/**
 * Zoom and pan of the full-screen image viewer.
 *
 * [scale] is relative to the fitted image ([FIT_SCALE]), [offset] is in the
 * viewer's own pixels and is applied *after* scaling, exactly as a
 * `graphicsLayer` applies `scaleX`/`translationX` — so a content point `p`
 * lands at `center + (p - center) * scale + offset`.
 */
@Immutable
internal data class ViewerTransform(
    val scale: Float = FIT_SCALE,
    val offset: Offset = Offset.Zero
)

/**
 * Size the image is actually drawn at inside [container] when fitted — the whole
 * image visible, aspect ratio kept. This is the size [ViewerTransform.scale]
 * multiplies, so every other bound derives from it.
 */
internal fun fittedSize(container: Size, aspectRatio: Float): Size {
    if (container.isEmpty() || aspectRatio <= 0f || !aspectRatio.isFinite()) return Size.Zero

    val byWidth = Size(container.width, container.width / aspectRatio)
    return when {
        byWidth.height <= container.height -> byWidth
        else -> Size(container.height * aspectRatio, container.height)
    }
}

/**
 * Scale at which one image pixel covers one screen pixel. Above [FIT_SCALE] for
 * an image that out-resolves the screen (a manga page, a scan), below it for one
 * the viewer has to upscale to fill the screen — a small illustration is only
 * sharp when zoomed *out* to this.
 */
internal fun oneToOneScale(imageWidth: Int, fitted: Size): Float {
    if (imageWidth <= 0 || fitted.width <= 0f) return FIT_SCALE
    return imageWidth / fitted.width
}

/** Lowest scale the viewer allows: fit, or 1:1 when that is smaller. */
internal fun minScaleFor(oneToOne: Float): Float = minOf(FIT_SCALE, oneToOne)

/** Highest scale the viewer allows: see [MIN_MAX_SCALE]. */
internal fun maxScaleFor(oneToOne: Float): Float = maxOf(MIN_MAX_SCALE, oneToOne)

/**
 * Where a double tap should take the viewer: back to fit when it is zoomed,
 * otherwise to the image's own pixels — or a plain magnification when the image
 * is already being shown at (near enough) its own resolution and 1:1 would be a
 * no-op.
 */
internal fun doubleTapScale(current: Float, oneToOne: Float): Float = when {
    !current.equalsScale(FIT_SCALE) -> FIT_SCALE
    !oneToOne.equalsScale(FIT_SCALE) -> oneToOne
    else -> PLAIN_ZOOM_SCALE
}

/**
 * Keeps the image covering the viewer: it may be dragged only as far as its own
 * edge. An axis the image does not fill on is pinned to the centre.
 */
internal fun clampOffset(
    offset: Offset,
    scale: Float,
    fitted: Size,
    container: Size
): Offset {
    val maxX = ((fitted.width * scale - container.width) / 2f).coerceAtLeast(0f)
    val maxY = ((fitted.height * scale - container.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Applies one step of a pinch/drag: the content under [anchor] stays under
 * [anchor] while the scale changes by [zoom], and the whole thing then moves by
 * [pan]. The result is clamped, so a gesture can never strand the image
 * off-screen.
 */
internal fun ViewerTransform.transformedBy(
    anchor: Offset,
    zoom: Float,
    pan: Offset,
    fitted: Size,
    container: Size,
    minScale: Float,
    maxScale: Float
): ViewerTransform {
    if (container.isEmpty() || fitted.isEmpty() || scale <= 0f) return this

    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
    val center = Offset(container.width / 2f, container.height / 2f)
    val anchorFromCenter = anchor - center

    // From `anchor = center + (p - center) * scale + offset`, solved for the
    // offset that leaves `p` where it is at the new scale.
    val offsetAtNewScale =
        anchorFromCenter - (anchorFromCenter - offset) * (newScale / scale) + pan

    return ViewerTransform(
        scale = newScale,
        offset = clampOffset(offsetAtNewScale, newScale, fitted, container)
    )
}

/**
 * Whether the image is taller than the viewer at [scale], i.e. a vertical drag
 * has somewhere to go. When it has not, that drag is free to mean "dismiss".
 */
internal fun canPanVertically(scale: Float, fitted: Size, container: Size): Boolean =
    fitted.height * scale > container.height + 0.5f

private fun Float.equalsScale(other: Float): Boolean =
    kotlin.math.abs(this - other) < SCALE_EPSILON
