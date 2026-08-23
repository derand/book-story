/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import android.os.Build
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderHorizontalGesture
import ua.acclorite.book_story.presentation.reader.model.ReaderTapPaging
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.common.components.common.AnimatedVisibility
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.common.SelectionContainer
import ua.acclorite.book_story.ui.common.components.common.SpacedItem
import ua.acclorite.book_story.ui.common.helpers.LocalActivity
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.reader.model.FontWithName
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment

@Composable
fun ReaderLayout(
    text: List<ReaderText>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    verticalPadding: Dp,
    horizontalGesture: ReaderHorizontalGesture,
    horizontalGestureSensitivity: Dp,
    horizontalGestureAlphaAnim: Boolean,
    horizontalGesturePullAnim: Boolean,
    tapPaging: ReaderTapPaging,
    disableScrolling: Boolean,
    pageTurnOverlap: Float,
    pageTurnAnimation: Boolean,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    progress: String,
    progressBar: Boolean,
    progressBarPadding: Dp,
    progressBarAlignment: HorizontalAlignment,
    progressBarFontSize: TextUnit,
    chapterBreak: Float,
    paragraphHeight: Dp,
    sidePadding: Dp,
    backgroundColor: Color,
    fontColor: Color,
    images: Boolean,
    imagesCaptions: Boolean,
    imagesCornersRoundness: Dp,
    imagesAlignment: HorizontalAlignment,
    imagesWidth: Float,
    imagesColorEffects: ColorFilter?,
    fontFamily: FontWithName,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    chapterTitleAlignment: ReaderTextAlignment,
    textAlignment: ReaderTextAlignment,
    horizontalAlignment: Alignment.Horizontal,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    paragraphIndentation: TextUnit,
    doubleClickTranslation: Boolean,
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    isLoading: Boolean,
    showMenu: Boolean,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit,
    openShareApp: (ReaderEvent.OnOpenShareApp) -> Unit,
    openWebBrowser: (ReaderEvent.OnOpenWebBrowser) -> Unit,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    openImage: (ReaderEvent.OnOpenImage) -> Unit
) {
    val activity = LocalActivity.current

    // Grouped once rather than filtered per item: a book of tens of thousands
    // of paragraphs would otherwise walk the whole match list for every one of
    // the dozen on screen.
    val matchesByItem = remember(searchMatches) {
        searchMatches.groupBy { match -> match.itemIndex }
    }

    // What one line of page-turn overlap is worth. Converting the line height
    // setting is not the same as asking what the text engine did with it: at a
    // system font scale of 1.3, `toDp()` puts a 22sp line at 37.5px where the
    // paragraph lays it out at 42.9px, the conversion applying a non-linear
    // font-scale curve the layout does not. So a sample is measured in the
    // paragraphs' own metrics instead, and the distance between two *inner*
    // lines is taken — the first line's top is trimmed, so a two-line sample
    // answers 37.5 as well. Only the fields that move that distance go into the
    // style.
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx = remember(
        textMeasurer, fontFamily, fontThickness, fontStyle, fontSize, lineHeight
    ) {
        val sample = textMeasurer.measure(
            text = "A\nA\nA",
            style = TextStyle(
                fontFamily = fontFamily.font,
                fontWeight = fontThickness.thickness,
                fontStyle = fontStyle,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
        )
        sample.getLineTop(2) - sample.getLineTop(1)
    }

    // The chapter break is measured in lines, and this is the line it counts:
    // the one the paragraphs are actually laid out at. Converting the
    // line-height setting instead would go through the non-linear font-scale
    // curve `toDp()` applies and the layout does not.
    val density = LocalDensity.current
    val lineHeightDp = remember(lineHeightPx, density) {
        with(density) { lineHeightPx.toDp() }
    }

    // Both triggers of a page turn — the tap zones and the horizontal swipe —
    // go through one pager, so the same action cannot behave two ways.
    val pager = rememberReaderPager(
        listState = listState,
        overlap = lineHeightPx * pageTurnOverlap,
        animate = pageTurnAnimation
    )
    val tapZones = remember(tapPaging) {
        when (tapPaging) {
            ReaderTapPaging.OFF -> null
            ReaderTapPaging.ON -> ReaderTapZones()
            ReaderTapPaging.INVERSE -> ReaderTapZones(inverted = true)
        }
    }

    ReaderFirstFrameTrace(hasText = text.isNotEmpty())
    SelectionContainer(
        onCopyRequested = {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                activity.getString(R.string.copied)
                    .showToast(context = activity, longToast = false)
            }
        },
        onShareRequested = { textToShare ->
            openShareApp(
                ReaderEvent.OnOpenShareApp(
                    textToShare = textToShare
                )
            )
        },
        onWebSearchRequested = { textToSearch ->
            openWebBrowser(
                ReaderEvent.OnOpenWebBrowser(
                    textToSearch = textToSearch
                )
            )
        },
        onTranslateRequested = { textToTranslate ->
            openTranslator(
                ReaderEvent.OnOpenTranslator(
                    textToTranslate = textToTranslate,
                    translateWholeParagraph = false
                )
            )
        },
    ) { toolbarHidden ->
        Column(
            Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .readerMenuTap(enabled = !isLoading && toolbarHidden) {
                    menuVisibility(
                        ReaderEvent.OnMenuVisibility(
                            show = !showMenu,
                            saveCheckpoint = true
                        )
                    )
                }
                .padding(contentPadding)
                .padding(vertical = verticalPadding)
                .readerHorizontalGesture(
                    pager = pager,
                    horizontalGesture = horizontalGesture,
                    horizontalGestureSensitivity = horizontalGestureSensitivity,
                    horizontalGestureAlphaAnim = horizontalGestureAlphaAnim,
                    horizontalGesturePullAnim = horizontalGesturePullAnim,
                    isLoading = isLoading
                )
        ) {
            LazyColumnWithScrollbar(
                state = listState,
                enableScrollbar = false,
                // The text viewport is what a page is measured against and what
                // the tap zones divide, so both are attached to it and to
                // nothing wider — the progress bar below is not part of a page.
                parentModifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { pager.viewportHeight = it.size.height.toFloat() }
                    .readerTapNavigation(
                        enabled = !isLoading && toolbarHidden,
                        zones = tapZones,
                        pager = pager,
                        listState = listState,
                        text = text,
                        images = images,
                        imagesWidth = imagesWidth,
                        sidePadding = sidePadding,
                        itemSpacing = paragraphHeight,
                        chapterBreak = chapterBreak,
                        lineHeight = lineHeightDp,
                        showMenu = showMenu,
                        doubleClickTranslation = doubleClickTranslation,
                        menuVisibility = menuVisibility,
                        openImage = openImage,
                        openTranslator = openTranslator
                    ),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = readerScrollEnabled(
                    disableScrolling = disableScrolling,
                    // A trigger counts only while it is attached. The tap zones
                    // come off with the text-selection toolbar, so for a reader
                    // who turns pages by tapping and has given up the scroll,
                    // selecting a word would otherwise leave the text answering
                    // to nothing until the selection was dismissed — the same
                    // dead end this guard exists for, only lasting a moment.
                    canTurnPage = horizontalGesture != ReaderHorizontalGesture.OFF ||
                            (tapZones != null && toolbarHidden)
                ),
                contentPadding = PaddingValues(
                    top = (WindowInsets.displayCutout.asPaddingValues()
                        .calculateTopPadding() + paragraphHeight)
                        .coerceAtLeast(18.dp),
                    bottom = (WindowInsets.displayCutout.asPaddingValues()
                        .calculateBottomPadding() + paragraphHeight)
                        .coerceAtLeast(18.dp),
                )
            ) {
                itemsIndexed(
                    text,
                    key = { index, _ -> index }
                ) { index, entry ->
                    when {
                        !images && entry is ReaderText.Image -> return@itemsIndexed
                        else -> {
                            SpacedItem(
                                index = index,
                                spacing = paragraphHeight
                            ) {
                                ReaderLayoutText(
                                    entry = entry,
                                    searchMatches = matchesByItem[index].orEmpty(),
                                    currentSearchMatch = currentSearchMatch,
                                    imagesCornersRoundness = imagesCornersRoundness,
                                    imagesAlignment = imagesAlignment,
                                    imagesWidth = imagesWidth,
                                    imagesColorEffects = imagesColorEffects,
                                    imagesCaptions = imagesCaptions,
                                    captionSpacing = paragraphHeight,
                                    fontFamily = fontFamily,
                                    fontColor = fontColor,
                                    lineHeight = lineHeight,
                                    fontThickness = fontThickness,
                                    fontStyle = fontStyle,
                                    chapterTitleAlignment = chapterTitleAlignment,
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

                                // A chapter boundary is marked below the
                                // chapter that ends, not above the title that
                                // begins — see [chapterBreakAfter].
                                val chapterBreak = chapterBreakAfter(
                                    text = text,
                                    index = index,
                                    images = images,
                                    lineHeight = lineHeightDp,
                                    breakLines = chapterBreak
                                )
                                if (chapterBreak > 0.dp) {
                                    Spacer(Modifier.height(chapterBreak))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showMenu && progressBar,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically()
            ) {
                ReaderProgressBar(
                    progress = progress,
                    progressBarPadding = progressBarPadding,
                    progressBarAlignment = progressBarAlignment,
                    progressBarFontSize = progressBarFontSize,
                    fontColor = fontColor,
                    sidePadding = sidePadding
                )
            }
        }
    }
}