/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Outline of the reserved slot; deliberately faint against the reading text. */
private const val OUTLINE_ALPHA = 0.12f
private const val ICON_ALPHA = 0.25f

private val MIN_ICON_SIZE = 16.dp
private val MAX_ICON_SIZE = 48.dp

/**
 * Stands in for an image whose bytes are not on hand: still loading in the
 * background, or [missing] for good (source file deleted, moved, or its
 * permission revoked).
 *
 * It fills the exact slot the image will occupy — the size comes from the cached
 * width/height, so nothing reflows when the image lands. No spinner and no blank
 * gap: the frame tells the reader something belongs there, and it stays put when
 * the image never arrives.
 *
 * Colours derive from the reader's own font colour, so it holds up on every
 * theme and custom color preset.
 */
@Composable
fun ReaderLayoutTextImagePlaceholder(
    modifier: Modifier,
    shape: Shape,
    fontColor: Color,
    missing: Boolean
) {
    BoxWithConstraints(
        modifier = modifier.border(
            width = 1.dp,
            color = fontColor.copy(alpha = OUTLINE_ALPHA),
            shape = shape
        ),
        contentAlignment = Alignment.Center
    ) {
        // Scaled to the slot so a small image does not get a huge glyph.
        val iconSize: Dp = (minOf(maxWidth, maxHeight) * 0.25f)
            .coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE)

        Icon(
            modifier = Modifier.size(iconSize),
            imageVector = when (missing) {
                true -> Icons.Outlined.BrokenImage
                false -> Icons.Outlined.Image
            },
            contentDescription = null,
            tint = fontColor.copy(alpha = ICON_ALPHA)
        )
    }
}
