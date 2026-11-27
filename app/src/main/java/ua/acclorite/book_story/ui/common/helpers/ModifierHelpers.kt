/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.helpers

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onLongClick

fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    return this.combinedClickable(
        indication = null,
        interactionSource = null,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick
    )
}

/**
 * A long press and nothing else, with no ripple.
 *
 * `noRippleClickable(onClick = {}, onLongClick = …)` also declares a click that
 * does nothing, so TalkBack offers "double-tap to activate" and then nothing
 * happens (#167). This exposes only the long-click action, and keeps the
 * haptic feedback `combinedClickable` gives a long press.
 */
fun Modifier.longPressable(onLongClick: () -> Unit): Modifier {
    return this then LongPressElement(onLongClick)
}

private data class LongPressElement(
    val onLongClick: () -> Unit
) : ModifierNodeElement<LongPressNode>() {
    override fun create() = LongPressNode(onLongClick)

    override fun update(node: LongPressNode) {
        node.onLongClick = onLongClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "longPressable"
    }
}

private class LongPressNode(
    var onLongClick: () -> Unit
) : DelegatingNode(), SemanticsModifierNode, CompositionLocalConsumerModifierNode {

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                detectTapGestures(onLongPress = { performLongClick() })
            }
        )
    }

    private fun performLongClick() {
        currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        onLongClick {
            performLongClick()
            true
        }
    }
}
