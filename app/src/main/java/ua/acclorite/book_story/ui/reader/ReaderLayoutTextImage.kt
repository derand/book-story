/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.common.helpers.LocalBookImages
import ua.acclorite.book_story.ui.common.helpers.noRippleClickable
import ua.acclorite.book_story.ui.reader.model.FontWithName
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment
import java.nio.ByteBuffer

@Composable
fun LazyItemScope.ReaderLayoutTextImage(
    entry: ReaderText.Image,
    showMenu: Boolean,
    sidePadding: Dp,
    imagesCornersRoundness: Dp,
    imagesAlignment: HorizontalAlignment,
    imagesWidth: Float,
    imagesColorEffects: ColorFilter?,
    imagesCaptions: Boolean,
    captionSpacing: Dp,
    fontFamily: FontWithName,
    fontColor: Color,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    textAlignment: ReaderTextAlignment,
    horizontalAlignment: Alignment.Horizontal,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    paragraphIndentation: TextUnit,
    doubleClickTranslation: Boolean,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    toolbarHidden: Boolean,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    openImage: (ReaderEvent.OnOpenImage) -> Unit,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit
) {
    val context = LocalContext.current
    // Bytes come from the store, not from the entry: on a parse-cache hit the
    // text arrives without them and they are filled in by a background load.
    val image = LocalBookImages.current[entry.image.src]
    val imageRequest = remember(image) {
        (image as? BookImage.Ready)?.let { ready ->
            ImageRequest.Builder(context)
                .data(ByteBuffer.wrap(ready.bytes))
                .memoryCacheKey(entry.image.id)
                .crossfade(100)
                .build()
        }
    }
    val shape = remember(imagesCornersRoundness) {
        RoundedCornerShape(imagesCornersRoundness)
    }

    Column(
        modifier = Modifier
            .animateItem(
                fadeInSpec = null,
                fadeOutSpec = null
            )
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(captionSpacing)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = sidePadding)
                .fillMaxWidth(),
            contentAlignment = imagesAlignment.alignment
        ) {
            // The slot is sized from the image's width/height either way, so an
            // image landing later cannot reflow the text around it.
            val slot = Modifier
                .clip(shape)
                .fillMaxWidth(imagesWidth)
                .aspectRatio(entry.image.aspectRatio)

            when (imageRequest) {
                null -> ReaderLayoutTextImagePlaceholder(
                    modifier = slot,
                    shape = shape,
                    fontColor = fontColor,
                    missing = image is BookImage.Missing
                )

                // A tap opens the image full screen; without a clickable of its
                // own it would fall through to the reader column and merely
                // toggle the menu. The placeholder keeps doing exactly that —
                // there is nothing to open until the bytes are in.
                else -> AsyncImage(
                    modifier = slot.noRippleClickable(
                        enabled = toolbarHidden,
                        onClick = { openImage(ReaderEvent.OnOpenImage(entry)) }
                    ),
                    model = imageRequest,
                    contentDescription = entry.caption?.line?.text,
                    colorFilter = imagesColorEffects,
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        if (imagesCaptions) {
            entry.caption?.let { caption ->
                ReaderLayoutTextParagraph(
                    paragraph = caption,
                    showMenu = showMenu,
                    fontFamily = fontFamily,
                    fontColor = fontColor,
                    lineHeight = lineHeight,
                    fontThickness = fontThickness,
                    fontStyle = fontStyle,
                    textAlignment = textAlignment,
                    horizontalAlignment = horizontalAlignment,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                    sidePadding = sidePadding,
                    paragraphIndentation = paragraphIndentation,
                    doubleClickTranslation = doubleClickTranslation,
                    highlightedReading = highlightedReading,
                    highlightedReadingThickness = highlightedReadingThickness,
                    toolbarHidden = toolbarHidden,
                    openTranslator = openTranslator,
                    openNote = openNote,
                    menuVisibility = menuVisibility
                )
            }
        }
    }
}
