/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import android.net.Uri
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R

/** A book's cover filling its box, or a grey picture icon when it has none. */
@Composable
fun BoxScope.BookCover(coverImage: Uri?, shape: Shape) {
    if (coverImage != null) {
        AsyncCoverImage(
            uri = coverImage,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = stringResource(id = R.string.cover_image_not_found_content_desc),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .aspectRatio(1f),
            tint = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
