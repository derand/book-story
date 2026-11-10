/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/**
 * How long a button with `disableOnClick` stays disabled after a tap. Long
 * enough to swallow a double or triple tap, short enough that a button whose
 * screen did not go away (a top bar faded back in, a pop that did nothing) is
 * usable again almost at once.
 */
private const val CLICK_GUARD_MS = 500L

/** Material's alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * Icon Button.
 * Can be disabled for a moment after a click, so user can't press button fast 2 or 3 times.
 *
 * @param modifier [Modifier] of the Icon.
 * @param icon ImageVector Icon.
 * @param contentDescription Content description (tooltip text).
 * @param disableOnClick Whether button disables for a moment after a click.
 * @param enabled Whether button enabled. A disabled button dims its icon.
 * @param color Color of the Icon.
 * @param onClick OnClick callback.
 */
@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    @StringRes contentDescription: Int,
    disableOnClick: Boolean,
    enabled: Boolean = true,
    color: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        disableOnClick = disableOnClick,
        enabled = enabled,
        color = color,
        onClick = onClick
    )
}

/**
 * Icon Button.
 * Can be disabled for a moment after a click, so user can't press button fast 2 or 3 times.
 *
 * @param modifier [Modifier] of the Icon.
 * @param icon Drawable Icon.
 * @param contentDescription Content description (tooltip text).
 * @param disableOnClick Whether button disables for a moment after a click.
 * @param enabled Whether button enabled. A disabled button dims its icon.
 * @param color Color of the Icon.
 * @param onClick OnClick callback.
 */
@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int,
    disableOnClick: Boolean,
    enabled: Boolean = true,
    color: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        painter = painterResource(id = icon),
        contentDescription = contentDescription,
        disableOnClick = disableOnClick,
        enabled = enabled,
        color = color,
        onClick = onClick
    )
}

@Composable
private fun IconButton(
    modifier: Modifier,
    painter: Painter,
    @StringRes contentDescription: Int,
    disableOnClick: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    var isClicked by remember { mutableStateOf(false) }
    // A timed guard, not a latch: the button may outlive its click (a top bar
    // shown again before its fade-out ended keeps its state), and a latch would
    // leave it dead for as long as it stays composed.
    LaunchedEffect(isClicked) {
        if (isClicked) {
            delay(CLICK_GUARD_MS)
            isClicked = false
        }
    }

    Tooltip(text = stringResource(id = contentDescription), enabled = enabled) {
        IconButton(
            enabled = enabled && !isClicked,
            modifier = Modifier.focusProperties {
                canFocus = false
            },
            onClick = {
                if (disableOnClick) {
                    isClicked = true
                }
                onClick()
            }
        ) {
            Icon(
                painter = painter,
                modifier = modifier,
                contentDescription = stringResource(id = contentDescription),
                // By `enabled` only: the short guard after a click must not flash.
                tint = if (enabled) color else color.copy(alpha = color.alpha * DISABLED_ALPHA)
            )
        }
    }
}
