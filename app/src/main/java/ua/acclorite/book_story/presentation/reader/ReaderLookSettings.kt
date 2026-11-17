/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.presentation.reader.model.ReaderColorEffects
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.reader.model.ReaderControls
import ua.acclorite.book_story.ui.reader.model.ReaderLook
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

/**
 * The reader's look from the current settings. Every setting is read with
 * `.value`, so changing any of them recomposes with a new look; the colours
 * come in from outside because they animate between presets.
 */
@Composable
fun SettingsManager.readerLook(fontColor: Color, backgroundColor: Color): ReaderLook {
    val density = LocalDensity.current
    val sidePaddingDp = ReaderLookUnits.sidePadding(sidePadding.value)
    val imagesWidthFraction = ReaderLookUnits.imagesWidth(imagesWidth.value)
    return ReaderLook(
        text = ReaderTextLook(
            fontFamily = fontFamily.value,
            fontColor = fontColor,
            fontSize = fontSize.value.sp,
            lineHeight = ReaderLookUnits.lineHeight(fontSize.value, lineHeight.value),
            letterSpacing = ReaderLookUnits.letterSpacing(letterSpacing.value),
            fontThickness = fontThickness.value,
            fontStyle = ReaderLookUnits.fontStyle(italic.value),
            textAlignment = textAlignment.value,
            chapterTitleAlignment = chapterTitleAlignment.value,
            horizontalAlignment = ReaderLookUnits.horizontalAlignment(textAlignment.value),
            paragraphIndentation = ReaderLookUnits.paragraphIndentation(
                paragraphIndentation.value,
                textAlignment.value
            ),
            sidePadding = sidePaddingDp,
            highlightedReading = highlightedReading.value,
            highlightedReadingThickness = ReaderLookUnits.highlightedReadingThickness(
                highlightedReadingThickness.value
            ),
            imagesCaptions = imagesCaptions.value,
            imagesCornersRoundness = ReaderLookUnits.imagesCornersRoundness(
                imagesCornersRoundness.value,
                imagesWidthFraction
            ),
            imagesAlignment = imagesAlignment.value,
            imagesWidth = imagesWidthFraction,
            imagesColorEffects = ReaderLookUnits.imagesColorEffects(
                imagesColorEffects.value,
                fontColor = fontColor,
                backgroundColor = backgroundColor
            )
        ),
        backgroundColor = backgroundColor,
        images = images.value,
        paragraphHeight = ReaderLookUnits.paragraphHeight(
            paragraphHeight.value,
            lineHeight.value,
            density
        ),
        verticalPadding = (verticalPadding.value * 4.5f).dp,
        chapterBreak = chapterBreak.value,
        bottomBarPadding = (bottomBarPadding.value * 4f).dp,
        perceptionExpander = perceptionExpander.value,
        perceptionExpanderPadding = sidePaddingDp + (perceptionExpanderPadding.value * 8).dp,
        perceptionExpanderThickness = (perceptionExpanderThickness.value * 0.25f).dp,
        horizontalLimiter = horizontalLimiter.value,
        horizontalLimiterHeight = (horizontalLimiterHeight.value * 20).dp,
        horizontalLimiterVerticalOffset = horizontalLimiterVerticalOffset.value,
        horizontalLimiterRuler = horizontalLimiterRuler.value,
        horizontalLimiterRulerThickness = (horizontalLimiterRulerThickness.value * 0.25f).dp,
        horizontalLimiterDimming = horizontalLimiterDimming.value,
        progressBar = progressBar.value,
        progressBarPadding = (progressBarPadding.value * 3).dp,
        progressBarAlignment = progressBarAlignment.value,
        progressBarFontSize = (progressBarFontSize.value * 2).sp
    )
}

/** The reader's gesture and key behaviour from the current settings. */
@Composable
fun SettingsManager.readerControls() = ReaderControls(
    horizontalGesture = horizontalGesture.value,
    horizontalGestureSensitivity = ReaderLookUnits.horizontalGestureSensitivity(
        horizontalGestureSensitivity.value
    ),
    horizontalGestureAlphaAnim = horizontalGestureAlphaAnim.value,
    horizontalGesturePullAnim = horizontalGesturePullAnim.value,
    tapPaging = tapPaging.value,
    volumePaging = volumePaging.value,
    disableScrolling = disableScrolling.value,
    pageTurnOverlap = pageTurnOverlap.value,
    pageTurnAnimation = pageTurnAnimation.value,
    doubleClickTranslation = doubleClickTranslation.value,
    fastColorPresetChange = fastColorPresetChange.value
)

/** The arithmetic that turns a stored setting into a size or a style. */
internal object ReaderLookUnits {

    fun lineHeight(fontSize: Int, lineHeight: Int): TextUnit = (fontSize + lineHeight).sp

    fun letterSpacing(letterSpacing: Int): TextUnit = (letterSpacing / 100f).em

    fun sidePadding(sidePadding: Int): Dp = (sidePadding * 3).dp

    /**
     * The gap between paragraphs, never less than half the line-height setting —
     * the setting alone, not the font size plus it, as it always was.
     */
    fun paragraphHeight(paragraphHeight: Int, lineHeight: Int, density: Density): Dp =
        (paragraphHeight * 3).dp.coerceAtLeast(
            with(density) { lineHeight.sp.toDp().value * 0.5f }.dp
        )

    fun fontStyle(italic: Boolean): FontStyle =
        if (italic) FontStyle.Italic else FontStyle.Normal

    /** Centred and end-aligned text gets no first-line indent. */
    fun paragraphIndentation(indentation: Int, alignment: ReaderTextAlignment): TextUnit =
        when (alignment) {
            ReaderTextAlignment.CENTER, ReaderTextAlignment.END -> 0.sp
            else -> (indentation * 6).sp
        }

    fun horizontalAlignment(alignment: ReaderTextAlignment): Alignment.Horizontal =
        when (alignment) {
            ReaderTextAlignment.START, ReaderTextAlignment.JUSTIFY -> Alignment.Start
            ReaderTextAlignment.CENTER -> Alignment.CenterHorizontally
            ReaderTextAlignment.END -> Alignment.End
        }

    fun highlightedReadingThickness(thickness: Int): FontWeight = when (thickness) {
        2 -> FontWeight.SemiBold
        3 -> FontWeight.Bold
        else -> FontWeight.Medium
    }

    fun imagesWidth(width: Float): Float = width.coerceAtLeast(0.01f)

    /** Scales with the image: a narrow image gets proportionally smaller corners. */
    fun imagesCornersRoundness(roundness: Int, imagesWidth: Float): Dp =
        (roundness * 3 * imagesWidth).dp

    fun imagesColorEffects(
        effects: ReaderColorEffects,
        fontColor: Color,
        backgroundColor: Color
    ): ColorFilter? = when (effects) {
        ReaderColorEffects.OFF -> null
        ReaderColorEffects.GRAYSCALE -> ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(0f) }
        )
        ReaderColorEffects.FONT -> ColorFilter.tint(color = fontColor, blendMode = BlendMode.Color)
        ReaderColorEffects.BACKGROUND -> ColorFilter.tint(
            color = backgroundColor,
            blendMode = BlendMode.Color
        )
    }

    /** 0 → 36 dp of pull before a page turns, 1 → 4 dp. */
    fun horizontalGestureSensitivity(sensitivity: Float): Dp =
        (36f + sensitivity * (4f - 36f)).dp
}
