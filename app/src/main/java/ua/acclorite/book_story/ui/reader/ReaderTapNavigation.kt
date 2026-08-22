/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.helpers.LocalBookImages

/**
 * The one place that decides what a tap on the reader's text area means.
 *
 * It is attached to the text viewport itself, so every position it works with is
 * already in the coordinates the zones are expressed in — an item cannot do this
 * for itself, since a gesture handler on a paragraph reports a position inside
 * *that paragraph* while a zone is a fraction of the screen. The items therefore
 * keep only what is genuinely theirs: hit-testing their own links, which they
 * consume, and which by consuming wins over everything decided here.
 *
 * The rule, in one sentence: **the middle acts on what is under your finger, the
 * edges turn the page**.
 */
@Composable
internal fun Modifier.readerTapNavigation(
    enabled: Boolean,
    zones: ReaderTapZones?,
    pager: ReaderPager,
    listState: LazyListState,
    text: List<ReaderText>,
    images: Boolean,
    imagesWidth: Float,
    sidePadding: Dp,
    itemSpacing: Dp,
    showMenu: Boolean,
    doubleClickTranslation: Boolean,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit,
    openImage: (ReaderEvent.OnOpenImage) -> Unit,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit
): Modifier {
    if (!enabled) return this

    // Read through state holders rather than keying the gesture on them: the
    // menu opens and closes on every tap, and restarting the pointer input each
    // time would drop the gesture that is still in flight.
    val currentZones = rememberUpdatedState(zones)
    val currentText = rememberUpdatedState(text)
    val currentImages = rememberUpdatedState(images)
    val currentImagesWidth = rememberUpdatedState(imagesWidth)
    val currentSidePadding = rememberUpdatedState(sidePadding)
    val currentBookImages = rememberUpdatedState(LocalBookImages.current)
    val currentShowMenu = rememberUpdatedState(showMenu)
    val currentTranslation = rememberUpdatedState(doubleClickTranslation)
    val currentMenuVisibility = rememberUpdatedState(menuVisibility)
    val currentOpenImage = rememberUpdatedState(openImage)
    val currentOpenTranslator = rememberUpdatedState(openTranslator)
    val haptics = LocalHapticFeedback.current

    return this.pointerInput(listState, pager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)

            fun toggleMenu() {
                currentMenuVisibility.value(
                    ReaderEvent.OnMenuVisibility(
                        show = !currentShowMenu.value,
                        saveCheckpoint = true
                    )
                )
            }

            // An open menu makes every tap a dismissal: turning a page while the
            // reader is trying to put the menu away would be nobody's intent.
            if (currentShowMenu.value) {
                val outcome = awaitPress(down)
                if (outcome is PressOutcome.Tap) {
                    outcome.change.consume()
                    toggleMenu()
                }
                return@awaitEachGesture
            }

            val hit = listState.entryHitAt(down.position.y, itemSpacing.toPx())
            val entry = hit?.let { currentText.value.getOrNull(it.index) }

            // A picture that has not arrived opens nothing: the viewer takes its
            // bytes from the same store and would put up an empty overlay. Its
            // placeholder is not a picture yet, so a tap on it is a tap on the
            // page, and the zone decides.
            val image = (entry as? ReaderText.Image)
                ?.takeIf { currentImages.value }
                ?.takeIf { currentBookImages.value[it.image.src] is BookImage.Ready }
            val zone = currentZones.value?.zoneAt(down.position, size.toSize())
                ?: ReaderTapZone.Center

            // Only an image waits out the long-press timeout, and only because a
            // long press is its second way of opening.
            when (val outcome = awaitPress(down, if (image != null) longPressTimeout else null)) {
                is PressOutcome.Cancelled -> return@awaitEachGesture

                // The moment the press becomes long is the moment it acts —
                // waiting for the finger to lift would make the gesture feel
                // like it had not registered. The buzz says so before the
                // viewer is even drawn, exactly as a long press does elsewhere.
                is PressOutcome.LongPress -> {
                    // A long press on the caption belongs to the text under it:
                    // the caption is the one part of an image entry a reader may
                    // want to select, and a tap already opens the picture.
                    val onPicture = image != null && hit != null && down.position.y <
                            hit.top + itemSpacing.toPx() + pictureHeight(
                        entryWidth = size.width.toFloat(),
                        sidePadding = currentSidePadding.value.toPx(),
                        widthFraction = currentImagesWidth.value,
                        aspectRatio = image.image.aspectRatio
                    )

                    if (onPicture) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOpenImage.value(ReaderEvent.OnOpenImage(image))
                        consumeUntilUp()
                    }
                }

                is PressOutcome.Tap -> {
                    outcome.change.consume()

                    // Double-tap translation reads the same anywhere on the
                    // screen: a tap on a paragraph waits to see whether a second
                    // one follows, edges included. It costs the double-tap
                    // timeout on every page turn — but only while the setting is
                    // on, and one gesture that means two things depending on
                    // where it landed would be worse than the wait.
                    val paragraph = entry?.translatableText()
                        ?.takeIf { currentTranslation.value }

                    if (paragraph != null && awaitSecondDown(outcome.change) != null) {
                        consumeUntilUp()
                        currentOpenTranslator.value(
                            ReaderEvent.OnOpenTranslator(
                                textToTranslate = paragraph,
                                translateWholeParagraph = true
                            )
                        )
                        return@awaitEachGesture
                    }

                    when {
                        zone != ReaderTapZone.Center -> pager.turn(zone == ReaderTapZone.Next)
                        image != null -> currentOpenImage.value(ReaderEvent.OnOpenImage(image))
                        else -> toggleMenu()
                    }
                }
            }
        }
    }
}

/**
 * How tall the picture inside an image entry is, in pixels. The caption sits
 * below it and is text, so the two have to be told apart, and only the layout
 * knows the rule: the picture is [widthFraction] of the width left by the side
 * padding, in its own aspect ratio.
 */
internal fun pictureHeight(
    entryWidth: Float,
    sidePadding: Float,
    widthFraction: Float,
    aspectRatio: Float
): Float {
    if (aspectRatio <= 0f || !aspectRatio.isFinite()) return 0f

    val width = (entryWidth - 2 * sidePadding).coerceAtLeast(0f) * widthFraction
    return width / aspectRatio
}

/**
 * The same notion of a tap, for the area *around* the text viewport — the
 * margins and the cutout padding, which no zone divides because they are not
 * part of a page. A tap there means what it has always meant, the menu; what
 * this adds is that a drag there is not a tap.
 *
 * It has to be said twice because `clickable` cannot say it: that cancels a
 * click when someone *consumes* the movement, which the list stops doing the
 * moment free scrolling is given up. The viewport's own handler runs first and
 * consumes the taps it acts on, so this one only ever sees what it left.
 */
@Composable
internal fun Modifier.readerMenuTap(enabled: Boolean, onTap: () -> Unit): Modifier {
    if (!enabled) return this
    val currentOnTap = rememberUpdatedState(onTap)

    return this
        // A raw `pointerInput` answers a finger and nothing else, and the
        // `clickable` it replaced was also what told the accessibility tree that
        // this screen can be activated at all. Without it a TalkBack double tap
        // reaches no click action anywhere in the reader, and the menu is the
        // only way from the reading screen to back, to settings and to the
        // chapters.
        .semantics {
            onClick {
                currentOnTap.value()
                true
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (awaitPress(down) is PressOutcome.Tap) currentOnTap.value()
            }
        }
}

/** What became of a press: it lifted, it was held, or something else took it. */
internal sealed interface PressOutcome {
    data class Tap(val change: PointerInputChange) : PressOutcome
    data object LongPress : PressOutcome
    data object Cancelled : PressOutcome
}

/**
 * Waits for the finger to lift without having travelled. [longPressTimeout]
 * (when given) cuts the wait short and reports a long press instead.
 *
 * A press that another handler consumed — a scroll, a link, the text selection —
 * counts as cancelled: it was never a tap on the page. So does one that moved
 * further than the touch slop, and that half cannot be left to the consuming:
 * with the scroll given up for a page-only reader the list consumes nothing, so
 * a drag of half a screen would otherwise arrive here as an unconsumed press and
 * be read as a tap — turning a page under a finger that was only moving.
 */
internal suspend fun AwaitPointerEventScope.awaitPress(
    down: PointerInputChange,
    longPressTimeout: Long? = null
): PressOutcome {
    suspend fun AwaitPointerEventScope.tapOrCancel(): PressOutcome {
        while (true) {
            val change = awaitPointerEvent().changes
                .firstOrNull { it.id == down.id }
                ?: return PressOutcome.Cancelled

            when {
                change.isConsumed -> return PressOutcome.Cancelled
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop ->
                    return PressOutcome.Cancelled

                !change.pressed -> return PressOutcome.Tap(change)
            }
        }
    }

    if (longPressTimeout == null) return tapOrCancel()

    return try {
        withTimeout(longPressTimeout) { tapOrCancel() }
    } catch (_: PointerEventTimeoutCancellationException) {
        PressOutcome.LongPress
    }
}

/**
 * Swallows what is left of the gesture, so that a press already acted upon does
 * not also reach the reader's own click handler when the finger finally lifts.
 */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

/** The second half of a double tap, if it arrives in time. */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
    firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
    val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
    var change: PointerInputChange
    do {
        change = awaitFirstDown()
    } while (change.uptimeMillis < minUptime)
    change
}

private val AwaitPointerEventScope.longPressTimeout: Long
    get() = viewConfiguration.longPressTimeoutMillis

/** Where an entry sits, in the same coordinates the finger is reported in. */
internal data class EntryHit(val index: Int, val top: Float)

/** One visible entry, as the list describes it: an offset and a size. */
internal data class EntryBounds(val index: Int, val offset: Int, val size: Int)

/**
 * The entry under [listY], which is the pointer's position **in the list's own
 * coordinates** — see the caller, which is where the two differ.
 *
 * The blank space between two entries is laid out as part of the lower one, and
 * is split here: each entry answers for the half of the gap that touches it.
 * Halves rather than all-or-nothing, because both ends of that choice are wrong
 * — give the whole gap to the entry below and the space above a picture opens
 * the picture; give it to no one and a one-line paragraph shrinks to a single
 * line of text, too small to double-tap.
 */
internal fun entryHitAt(listY: Float, items: List<EntryBounds>, spacing: Float): EntryHit? {
    val item = items.firstOrNull { listY >= it.offset && listY < it.offset + it.size }
        ?: return null

    return when {
        item.index > 0 && listY < item.offset + spacing / 2f ->
            items.firstOrNull { it.index == item.index - 1 }
                ?.let { EntryHit(it.index, it.offset.toFloat()) }
                ?: EntryHit(item.index - 1, item.offset.toFloat())

        else -> EntryHit(item.index, item.offset.toFloat())
    }
}

/**
 * The same question asked of a live list, where the pointer's `y` and the items'
 * offsets are **not** in the same coordinates: an item's offset is measured from
 * where the content begins, while the finger is reported from the top edge of
 * the viewport, which is the content padding higher up. `viewportStartOffset` is
 * that difference (it is `-beforeContentPadding`), and forgetting it moves every
 * tap a padding's worth down the page — enough to answer with the entry below
 * the finger.
 */
private fun LazyListState.entryHitAt(y: Float, spacing: Float): EntryHit? {
    val start = layoutInfo.viewportStartOffset
    val hit = entryHitAt(
        listY = y + start,
        items = layoutInfo.visibleItemsInfo.map { EntryBounds(it.index, it.offset, it.size) },
        spacing = spacing
    ) ?: return null

    return hit.copy(top = hit.top - start)
}

/** The text a double tap sends to the translator, or null when there is none. */
private fun ReaderText.translatableText(): String? = when (this) {
    is ReaderText.Text -> line.text
    is ReaderText.Poem -> lines.joinToString("\n") { it.line.text }
    else -> null
}?.takeIf { it.isNotBlank() }
