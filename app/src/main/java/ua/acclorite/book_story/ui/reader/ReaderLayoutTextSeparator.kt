/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

@Composable
fun LazyItemScope.ReaderLayoutTextSeparator(
    look: ReaderTextLook,
) {
    HorizontalDivider(
        thickness = 3.dp,
        modifier = Modifier
            .animateItem(
                fadeInSpec = null,
                fadeOutSpec = null
            )
            .padding(horizontal = look.sidePadding)
            .clip(CircleShape),
        color = look.fontColor.copy(0.3f)
    )
}