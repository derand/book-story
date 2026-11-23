/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.presentation.settings.ColorPresetsEvent

/**
 * How far a horizontal swipe has to travel to switch the colour preset. In dp,
 * so it is the same distance on every screen: 200 px on the 1.5x tablet it
 * was first set on.
 */
private val SWITCH_THRESHOLD = 133.dp

@Composable
fun Modifier.readerColorPresetChange(
    colorPresetChangeEnabled: Boolean,
    isLoading: Boolean,
    switchColorPreset: (ColorPresetsEvent.OnSwitchColorPreset) -> Unit
): Modifier {
    val offset = remember { mutableFloatStateOf(0f) }
    return this.then(
        if (colorPresetChangeEnabled && !isLoading) {
            Modifier.pointerInput(Unit) {
                val threshold = SWITCH_THRESHOLD.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset.floatValue = 0f },
                    onDragCancel = { offset.floatValue = 0f },
                    onDragEnd = {
                        when {
                            offset.floatValue > threshold -> {
                                switchColorPreset(
                                    ColorPresetsEvent.OnSwitchColorPreset(
                                        previous = true
                                    )
                                )
                            }

                            offset.floatValue < -threshold -> {
                                switchColorPreset(
                                    ColorPresetsEvent.OnSwitchColorPreset(
                                        previous = false
                                    )
                                )
                            }
                        }
                    }
                ) { _, dragAmount ->
                    offset.floatValue += dragAmount
                }
            }
        } else Modifier
    )
}