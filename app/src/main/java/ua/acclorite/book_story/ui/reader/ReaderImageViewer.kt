/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.helpers.LocalBookImages
import kotlin.math.abs
import coil.size.Size as CoilSize

/** Opacity of the backdrop the reader is dimmed behind. */
private const val SCRIM_ALPHA = 0.94f

/** Fraction of the viewer height a downward swipe must cover to dismiss. */
private const val DISMISS_DISTANCE_FRACTION = 0.15f

/** Swipe distance, as a fraction of the height, over which the scrim fades out. */
private const val DISMISS_FADE_FRACTION = 0.5f

/**
 * Full-screen view of one book image: fitted on open, then free to be pinched,
 * panned and double-tapped up to its own pixels. It exists because the page
 * renders every image at the same width — deliberately, so illustrations line
 * up — which leaves a wide panorama or a dense manga page unreadable in place.
 *
 * Rendering in the page is not touched: this draws over the reader, and the
 * scroll position underneath is untouched too.
 *
 * The image is requested at its original size (the in-page one is decoded to the
 * width of its slot), under the same memory cache key — Coil rejects the smaller
 * cached bitmap for this request and keeps the bigger one afterwards, so at most
 * one bitmap per image is held.
 */
@Composable
fun ReaderImageViewer(
    entry: ReaderText.Image,
    dismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The image comes from the store, exactly as for the in-page one; only a
    // Ready image is tappable, so it is on hand by the time this opens.
    val image = LocalBookImages.current[entry.image.src] as? BookImage.Ready
    val imageRequest = remember(image) {
        image?.let {
            ImageRequest.Builder(context)
                .data(it.imageData())
                .memoryCacheKey(entry.image.id)
                .size(CoilSize.ORIGINAL)
                .build()
        }
    }

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var transform by remember { mutableStateOf(ViewerTransform()) }
    // Vertical travel of the swipe-to-dismiss gesture; animated so a swipe that
    // does not reach the threshold springs back instead of sticking.
    val dismissDrag = remember { Animatable(0f) }

    val fitted = remember(containerSize, entry.image.aspectRatio) {
        fittedSize(containerSize, entry.image.aspectRatio)
    }
    val oneToOne = remember(fitted, entry.image.width) {
        oneToOneScale(entry.image.width, fitted)
    }
    val minScale = minScaleFor(oneToOne)
    val maxScale = maxScaleFor(oneToOne)

    val dismissProgress = when {
        containerSize.height <= 0f -> 0f
        else -> (dismissDrag.value / (containerSize.height * DISMISS_FADE_FRACTION))
            .coerceIn(0f, 1f)
    }

    // The activity handles orientation changes itself, so a rotation does not
    // recreate this — the zoom and pan of the *old* screen survive into a viewer
    // they no longer fit, and would leave the image stranded off-screen until the
    // next gesture. Re-run the transform against the new geometry: a no-op zoom
    // about the centre is enough, since that is what clamps the offset and pulls
    // the scale back inside the new bounds.
    LaunchedEffect(fitted) {
        transform = transform.transformedBy(
            anchor = Offset(containerSize.width / 2f, containerSize.height / 2f),
            zoom = 1f,
            pan = Offset.Zero,
            fitted = fitted,
            container = containerSize,
            minScale = minScale,
            maxScale = maxScale
        )
    }

    BackHandler { dismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA * (1f - dismissProgress)))
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            // Every bound below is derived from the fitted size, so that alone
            // says whether the detector needs restarting.
            .pointerInput(fitted) {
                detectViewerGestures(
                    onTransform = { centroid, pan, zoom ->
                        val dismissing = zoom == 1f &&
                                abs(pan.y) > abs(pan.x) &&
                                !canPanVertically(transform.scale, fitted, containerSize)

                        when {
                            dismissing -> scope.launch {
                                dismissDrag.snapTo(
                                    (dismissDrag.value + pan.y).coerceAtLeast(0f)
                                )
                            }

                            else -> transform = transform.transformedBy(
                                anchor = centroid,
                                zoom = zoom,
                                pan = pan,
                                fitted = fitted,
                                container = containerSize,
                                minScale = minScale,
                                maxScale = maxScale
                            )
                        }
                    },
                    onGestureEnd = {
                        val threshold = containerSize.height * DISMISS_DISTANCE_FRACTION
                        when {
                            dismissDrag.value > threshold -> dismiss()
                            else -> scope.launch { dismissDrag.animateTo(0f) }
                        }
                    },
                    onDoubleTap = { position ->
                        val target = doubleTapScale(transform.scale, oneToOne)
                        transform = transform.transformedBy(
                            anchor = position,
                            zoom = target / transform.scale,
                            pan = Offset.Zero,
                            fitted = fitted,
                            container = containerSize,
                            minScale = minScale,
                            maxScale = maxScale
                        )
                    }
                )
            }
    ) {
        if (imageRequest != null) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y + dismissDrag.value
                    },
                model = imageRequest,
                contentDescription = entry.caption?.line?.text,
                contentScale = ContentScale.Fit
            )
        }

        IconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .alpha(1f - dismissProgress)
                .systemBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            onClick = dismiss
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close_image_content_desc),
                tint = Color.White
            )
        }

        entry.caption?.let { caption ->
            StyledText(
                text = caption.line,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .alpha(1f - dismissProgress)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

/**
 * Every gesture the viewer understands, in one loop: pinch, drag, and the double
 * tap. Deliberately *one* detector rather than
 * [androidx.compose.foundation.gestures.detectTransformGestures] alongside
 * [androidx.compose.foundation.gestures.detectTapGestures]:
 *
 * - two detectors in one `pointerInput` are dispatched in *reverse* registration
 *   order on the main pass, and the tap detector consumes the first `down` —
 *   which swallowed the pinch a user made right after opening the viewer;
 * - `detectTransformGestures` has no end callback anyway, and swipe-to-dismiss
 *   has to choose between dismissing and springing back at the moment the
 *   fingers leave the screen.
 *
 * Nothing is reported or consumed until the gesture passes the touch slop, so a
 * gesture that stays still is free to end up being read as a tap.
 */
private suspend fun PointerInputScope.detectViewerGestures(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit,
    onDoubleTap: (position: Offset) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var multitouch = false
        var canceled = false
        var lastChange = awaitFirstDown(requireUnconsumed = false)

        while (true) {
            val event = awaitPointerEvent()
            canceled = event.changes.any { it.isConsumed }
            if (canceled) break
            if (event.changes.size > 1) multitouch = true

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastTouchSlop) {
                zoom *= zoomChange
                pan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1 - zoom) * centroidSize
                if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                    pastTouchSlop = true
                }
            }

            if (pastTouchSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onTransform(centroid, panChange, zoomChange)
                }
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            }

            lastChange = event.changes.last()
            if (event.changes.none { it.pressed }) break
        }

        val tappedTwice = lastChange.uptimeMillis - lastTapTime < doubleTapTimeout &&
                (lastChange.position - lastTapPosition).getDistance() <= touchSlop * 2

        when {
            pastTouchSlop -> onGestureEnd()
            canceled || multitouch -> Unit

            tappedTwice -> {
                lastTapTime = 0L
                onDoubleTap(lastChange.position)
            }

            else -> {
                lastTapTime = lastChange.uptimeMillis
                lastTapPosition = lastChange.position
            }
        }
    }
}
