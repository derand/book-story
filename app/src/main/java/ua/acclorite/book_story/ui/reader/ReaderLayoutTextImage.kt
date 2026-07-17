/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment
import java.nio.ByteBuffer

@Composable
fun LazyItemScope.ReaderLayoutTextImage(
    entry: ReaderText.Image,
    sidePadding: Dp,
    imagesCornersRoundness: Dp,
    imagesAlignment: HorizontalAlignment,
    imagesWidth: Float,
    imagesColorEffects: ColorFilter?
) {
    val context = LocalContext.current
    val imageRequest = remember(entry.image) {
        ImageRequest.Builder(context)
            .data(ByteBuffer.wrap(entry.image.bytes))
            .memoryCacheKey(entry.image.id)
            .crossfade(100)
            .build()
    }

    Box(
        modifier = Modifier
            .animateItem(
                fadeInSpec = null,
                fadeOutSpec = null
            )
            .padding(horizontal = sidePadding)
            .fillMaxWidth(),
        contentAlignment = imagesAlignment.alignment
    ) {
        AsyncImage(
            modifier = Modifier
                .clip(RoundedCornerShape(imagesCornersRoundness))
                .fillMaxWidth(imagesWidth)
                .aspectRatio(entry.image.aspectRatio),
            model = imageRequest,
            contentDescription = null,
            colorFilter = imagesColorEffects,
            contentScale = ContentScale.FillWidth
        )
    }
}
