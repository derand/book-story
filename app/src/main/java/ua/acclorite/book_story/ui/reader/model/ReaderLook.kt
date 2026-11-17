/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderHorizontalGesture
import ua.acclorite.book_story.presentation.reader.model.ReaderTapPaging
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.presentation.reader.model.ReaderVolumePaging
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment

/**
 * How the reader draws a book, with the reader settings already turned into
 * sizes and styles. Built once by the screen and handed down whole, so a new
 * look setting touches this class and its mapping, not every level between.
 */
@Immutable
data class ReaderLook(
    val text: ReaderTextLook,
    val backgroundColor: Color,
    val images: Boolean,
    val paragraphHeight: Dp,
    val verticalPadding: Dp,
    val chapterBreak: Float,
    val bottomBarPadding: Dp,
    val perceptionExpander: Boolean,
    val perceptionExpanderPadding: Dp,
    val perceptionExpanderThickness: Dp,
    val horizontalLimiter: Boolean,
    val horizontalLimiterHeight: Dp,
    val horizontalLimiterVerticalOffset: Float,
    val horizontalLimiterRuler: Boolean,
    val horizontalLimiterRulerThickness: Dp,
    val horizontalLimiterDimming: Float,
    val progressBar: Boolean,
    val progressBarPadding: Dp,
    val progressBarAlignment: HorizontalAlignment,
    val progressBarFontSize: TextUnit
)

/** The part of [ReaderLook] each text entry of the book is drawn with. */
@Immutable
data class ReaderTextLook(
    val fontFamily: FontWithName,
    val fontColor: Color,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val letterSpacing: TextUnit,
    val fontThickness: ReaderFontThickness,
    val fontStyle: FontStyle,
    val textAlignment: ReaderTextAlignment,
    val chapterTitleAlignment: ReaderTextAlignment,
    val horizontalAlignment: Alignment.Horizontal,
    val paragraphIndentation: TextUnit,
    val sidePadding: Dp,
    val highlightedReading: Boolean,
    val highlightedReadingThickness: FontWeight,
    val imagesCaptions: Boolean,
    val imagesCornersRoundness: Dp,
    val imagesAlignment: HorizontalAlignment,
    val imagesWidth: Float,
    val imagesColorEffects: ColorFilter?
)

/** How the reader answers gestures and keys — the behaviour settings. */
@Immutable
data class ReaderControls(
    val horizontalGesture: ReaderHorizontalGesture,
    val horizontalGestureSensitivity: Dp,
    val horizontalGestureAlphaAnim: Boolean,
    val horizontalGesturePullAnim: Boolean,
    val tapPaging: ReaderTapPaging,
    val volumePaging: ReaderVolumePaging,
    val disableScrolling: Boolean,
    val pageTurnOverlap: Float,
    val pageTurnAnimation: Boolean,
    val doubleClickTranslation: Boolean,
    val fastColorPresetChange: Boolean
)
