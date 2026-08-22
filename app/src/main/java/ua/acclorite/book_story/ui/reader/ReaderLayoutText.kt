/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.reader.model.FontWithName
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment

/**
 * One entry of the book. What a *tap* on it means is not decided here — see
 * [readerTapNavigation]; the only gesture an entry owns is the hit test for its
 * own links, which is why [openNote] is all that is passed down.
 */
@Composable
fun LazyItemScope.ReaderLayoutText(
    entry: ReaderText,
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
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
    chapterTitleAlignment: ReaderTextAlignment,
    textAlignment: ReaderTextAlignment,
    horizontalAlignment: Alignment.Horizontal,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    sidePadding: Dp,
    paragraphIndentation: TextUnit,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    toolbarHidden: Boolean,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    when (entry) {
        is ReaderText.Image -> {
            ReaderLayoutTextImage(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                entry = entry,
                sidePadding = sidePadding,
                imagesCornersRoundness = imagesCornersRoundness,
                imagesAlignment = imagesAlignment,
                imagesWidth = imagesWidth,
                imagesColorEffects = imagesColorEffects,
                imagesCaptions = imagesCaptions,
                captionSpacing = captionSpacing,
                fontFamily = fontFamily,
                fontColor = fontColor,
                lineHeight = lineHeight,
                fontThickness = fontThickness,
                fontStyle = fontStyle,
                textAlignment = textAlignment,
                horizontalAlignment = horizontalAlignment,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                paragraphIndentation = paragraphIndentation,
                highlightedReading = highlightedReading,
                highlightedReadingThickness = highlightedReadingThickness,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }

        is ReaderText.Separator -> {
            ReaderLayoutTextSeparator(
                sidePadding = sidePadding,
                fontColor = fontColor
            )
        }

        is ReaderText.Table -> {
            ReaderLayoutTextTable(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                table = entry,
                fontFamily = fontFamily,
                fontColor = fontColor,
                lineHeight = lineHeight,
                fontThickness = fontThickness,
                fontStyle = fontStyle,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                sidePadding = sidePadding
            )
        }

        is ReaderText.Chapter -> {
            ReaderLayoutTextChapter(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                chapter = entry,
                chapterTitleAlignment = chapterTitleAlignment,
                fontColor = fontColor,
                sidePadding = sidePadding,
                highlightedReading = highlightedReading,
                highlightedReadingThickness = highlightedReadingThickness,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }

        is ReaderText.Poem -> {
            ReaderLayoutTextPoem(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                poem = entry,
                fontFamily = fontFamily,
                fontColor = fontColor,
                lineHeight = lineHeight,
                fontThickness = fontThickness,
                fontStyle = fontStyle,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                sidePadding = sidePadding,
                highlightedReading = highlightedReading,
                highlightedReadingThickness = highlightedReadingThickness,
                openNote = openNote
            )
        }

        is ReaderText.Text -> {
            ReaderLayoutTextParagraph(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                paragraph = entry,
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
                highlightedReading = highlightedReading,
                highlightedReadingThickness = highlightedReadingThickness,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }
    }
}
