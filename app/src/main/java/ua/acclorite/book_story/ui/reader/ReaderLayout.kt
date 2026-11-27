/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderHorizontalGesture
import ua.acclorite.book_story.presentation.reader.model.ReaderTapPaging
import ua.acclorite.book_story.presentation.reader.model.ReaderVolumePaging
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.common.SelectionContainer
import ua.acclorite.book_story.ui.common.components.common.SpacedItem
import ua.acclorite.book_story.ui.common.helpers.showToast
import ua.acclorite.book_story.ui.reader.model.ReaderControls
import ua.acclorite.book_story.ui.reader.model.ReaderLook

@Composable
fun ReaderLayout(
    text: List<ReaderText>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    look: ReaderLook,
    controls: ReaderControls,
    progress: String,
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    isLoading: Boolean,
    showMenu: Boolean,
    onEvent: (ReaderEvent) -> Unit
) {
    val activity = checkNotNull(LocalActivity.current)

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
        textMeasurer,
        look.text.fontFamily,
        look.text.fontThickness,
        look.text.fontStyle,
        look.text.fontSize,
        look.text.lineHeight
    ) {
        val sample = textMeasurer.measure(
            text = "A\nA\nA",
            style = TextStyle(
                fontFamily = look.text.fontFamily.font,
                fontWeight = look.text.fontThickness.thickness,
                fontStyle = look.text.fontStyle,
                fontSize = look.text.fontSize,
                lineHeight = look.text.lineHeight
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

    // Every trigger of a page turn — the tap zones, the horizontal swipe and
    // the volume keys — goes through one pager, so the same action cannot
    // behave two ways.
    val pager = rememberReaderPager(
        listState = listState,
        overlap = lineHeightPx * controls.pageTurnOverlap,
        animate = controls.pageTurnAnimation
    )
    val tapZones = remember(controls.tapPaging) {
        when (controls.tapPaging) {
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
            onEvent(
                ReaderEvent.OnOpenShareApp(
                    textToShare = textToShare
                )
            )
        },
        onWebSearchRequested = { textToSearch ->
            onEvent(
                ReaderEvent.OnOpenWebBrowser(
                    textToSearch = textToSearch
                )
            )
        },
        onTranslateRequested = { textToTranslate ->
            onEvent(
                ReaderEvent.OnOpenTranslator(
                    textToTranslate = textToTranslate,
                    translateWholeParagraph = false
                )
            )
        },
    ) { toolbarHidden ->
        // The keys are attached on the same terms as the tap zones, and on one
        // more: while the menu is open they belong to the system again, which
        // is the only way to change the volume with this on.
        ReaderVolumeKeys(
            enabled = !isLoading && toolbarHidden && !showMenu,
            volumePaging = controls.volumePaging,
            pager = pager
        )

        Column(
            Modifier
                .fillMaxSize()
                .background(look.backgroundColor)
                .readerMenuTap(enabled = !isLoading && toolbarHidden) {
                    onEvent(
                        ReaderEvent.OnMenuVisibility(
                            show = !showMenu,
                            saveCheckpoint = true
                        )
                    )
                }
                .padding(contentPadding)
                .padding(vertical = look.verticalPadding)
                .readerHorizontalGesture(
                    pager = pager,
                    horizontalGesture = controls.horizontalGesture,
                    horizontalGestureSensitivity = controls.horizontalGestureSensitivity,
                    horizontalGestureAlphaAnim = controls.horizontalGestureAlphaAnim,
                    horizontalGesturePullAnim = controls.horizontalGesturePullAnim,
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
                        images = look.images,
                        imagesWidth = look.text.imagesWidth,
                        sidePadding = look.text.sidePadding,
                        itemSpacing = look.paragraphHeight,
                        chapterBreak = look.chapterBreak,
                        lineHeight = lineHeightDp,
                        showMenu = showMenu,
                        doubleClickTranslation = controls.doubleClickTranslation,
                        menuVisibility = onEvent,
                        openImage = onEvent,
                        openTranslator = onEvent
                    ),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = readerScrollEnabled(
                    disableScrolling = controls.disableScrolling,
                    // A trigger counts only while it is attached. The tap zones
                    // come off with the text-selection toolbar, so for a reader
                    // who turns pages by tapping and has given up the scroll,
                    // selecting a word would otherwise leave the text answering
                    // to nothing until the selection was dismissed — the same
                    // dead end this guard exists for, only lasting a moment.
                    canTurnPage = controls.horizontalGesture != ReaderHorizontalGesture.OFF ||
                            (tapZones != null && toolbarHidden) ||
                            // The menu is not counted here, unlike above: it is
                            // dismissed by the same tap that would have turned a
                            // page, so the reader is one tap from the trigger
                            // rather than stranded without one.
                            (controls.volumePaging != ReaderVolumePaging.OFF && toolbarHidden)
                ),
                contentPadding = PaddingValues(
                    top = (WindowInsets.displayCutout.asPaddingValues()
                        .calculateTopPadding() + look.paragraphHeight)
                        .coerceAtLeast(18.dp),
                    bottom = (WindowInsets.displayCutout.asPaddingValues()
                        .calculateBottomPadding() + look.paragraphHeight)
                        .coerceAtLeast(18.dp),
                )
            ) {
                itemsIndexed(text) { index, entry ->
                    when {
                        !look.images && entry is ReaderText.Image -> return@itemsIndexed
                        else -> {
                            SpacedItem(
                                index = index,
                                spacing = look.paragraphHeight
                            ) {
                                ReaderLayoutText(
                                    entry = entry,
                                    searchMatches = matchesByItem[index].orEmpty(),
                                    currentSearchMatch = currentSearchMatch,
                                    look = look.text,
                                    captionSpacing = look.paragraphHeight,
                                    toolbarHidden = toolbarHidden,
                                    openNote = onEvent
                                )

                                // A chapter boundary is marked below the
                                // chapter that ends, not above the title that
                                // begins — see [chapterBreakAfter].
                                val breakHeight = chapterBreakAfter(
                                    text = text,
                                    index = index,
                                    images = look.images,
                                    lineHeight = lineHeightDp,
                                    breakLines = look.chapterBreak
                                )
                                if (breakHeight > 0.dp) {
                                    Spacer(Modifier.height(breakHeight))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showMenu && look.progressBar,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically()
            ) {
                ReaderProgressBar(
                    progress = progress,
                    progressBarPadding = look.progressBarPadding,
                    progressBarAlignment = look.progressBarAlignment,
                    progressBarFontSize = look.progressBarFontSize,
                    fontColor = look.text.fontColor,
                    sidePadding = look.text.sidePadding
                )
            }
        }
    }
}