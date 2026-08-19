/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.presentation.reader.ReaderEvent

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
                val outcome = awaitPress()
                if (outcome is PressOutcome.Tap) {
                    outcome.change.consume()
                    toggleMenu()
                }
                return@awaitEachGesture
            }

            val entry = currentText.value
                .getOrNull(listState.entryIndexAt(down.position.y, itemSpacing.toPx()))
            val image = (entry as? ReaderText.Image)?.takeIf { currentImages.value }
            val zone = currentZones.value?.zoneAt(down.position, size.toSize())
                ?: ReaderTapZone.Center

            // Only an image waits out the long-press timeout, and only because a
            // long press is its second way of opening.
            when (val outcome = awaitPress(if (image != null) longPressTimeout else null)) {
                is PressOutcome.Cancelled -> return@awaitEachGesture

                // The moment the press becomes long is the moment it acts —
                // waiting for the finger to lift would make the gesture feel
                // like it had not registered. The buzz says so before the
                // viewer is even drawn, exactly as a long press does elsewhere.
                is PressOutcome.LongPress -> {
                    image?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOpenImage.value(ReaderEvent.OnOpenImage(it))
                    }
                    consumeUntilUp()
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

/** What became of a press: it lifted, it was held, or something else took it. */
private sealed interface PressOutcome {
    data class Tap(val change: PointerInputChange) : PressOutcome
    data object LongPress : PressOutcome
    data object Cancelled : PressOutcome
}

/**
 * Waits for the finger to lift. [longPressTimeout] (when given) cuts the wait
 * short and reports a long press instead.
 *
 * A press that another handler consumed — a scroll, a link, the text selection —
 * counts as cancelled: it was never a tap on the page.
 */
private suspend fun AwaitPointerEventScope.awaitPress(
    longPressTimeout: Long? = null
): PressOutcome {
    suspend fun AwaitPointerEventScope.tapOrCancel(): PressOutcome {
        val up = waitForUpOrCancellation() ?: return PressOutcome.Cancelled
        return if (up.isConsumed) PressOutcome.Cancelled else PressOutcome.Tap(up)
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

/**
 * The entry under [y], in the coordinates of the list's own viewport.
 *
 * The blank space between two entries is laid out as part of the lower one, and
 * is split here: each entry answers for the half of the gap that touches it.
 * Halves rather than all-or-nothing, because both ends of that choice are wrong
 * — give the whole gap to the entry below and the space above a picture opens
 * the picture; give it to no one and a one-line paragraph shrinks to a single
 * line of text, too small to double-tap.
 */
private fun LazyListState.entryIndexAt(y: Float, spacing: Float): Int {
    val item = layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y < it.offset + it.size }
        ?: return -1

    return when {
        item.index > 0 && y < item.offset + spacing / 2f -> item.index - 1
        else -> item.index
    }
}

/** The text a double tap sends to the translator, or null when there is none. */
private fun ReaderText.translatableText(): String? = when (this) {
    is ReaderText.Text -> line.text
    is ReaderText.Poem -> lines.joinToString("\n") { it.line.text }
    else -> null
}?.takeIf { it.isNotBlank() }
