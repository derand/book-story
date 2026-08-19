/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import ua.acclorite.book_story.core.BottomSheet
import ua.acclorite.book_story.core.Drawer
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book
import androidx.compose.ui.text.AnnotatedString
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.ReaderScreen
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderHorizontalGesture
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.reader.model.ReaderTapPaging
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.ui.reader.model.FontWithName
import ua.acclorite.book_story.ui.theme.model.HorizontalAlignment

@Composable
fun ReaderContent(
    book: Book,
    text: List<ReaderText>,
    bottomSheet: BottomSheet?,
    drawer: Drawer?,
    currentNote: AnnotatedString?,
    fullscreenImage: ReaderText.Image?,
    listState: LazyListState,
    currentChapter: Chapter?,
    nestedScrollConnection: NestedScrollConnection,
    fastColorPresetChange: Boolean,
    perceptionExpander: Boolean,
    perceptionExpanderPadding: Dp,
    perceptionExpanderThickness: Dp,
    horizontalLimiter: Boolean,
    horizontalLimiterHeight: Dp,
    horizontalLimiterVerticalOffset: Float,
    horizontalLimiterRulerThickness: Dp,
    horizontalLimiterRuler: Boolean,
    horizontalLimiterDimming: Float,
    currentChapterProgress: Float,
    isLoading: Boolean,
    errorMessage: UIText?,
    checkpoints: List<Checkpoint>,
    search: ReaderSearch,
    showMenu: Boolean,
    lockMenu: Boolean,
    contentPadding: PaddingValues,
    verticalPadding: Dp,
    horizontalGesture: ReaderHorizontalGesture,
    horizontalGestureScroll: Float,
    horizontalGestureSensitivity: Dp,
    horizontalGestureAlphaAnim: Boolean,
    horizontalGesturePullAnim: Boolean,
    horizontalGestureDisableScrolling: Boolean,
    tapPaging: ReaderTapPaging,
    pageTurnAnimation: Boolean,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    progress: String,
    progressBar: Boolean,
    progressBarPadding: Dp,
    progressBarAlignment: HorizontalAlignment,
    progressBarFontSize: TextUnit,
    paragraphHeight: Dp,
    sidePadding: Dp,
    bottomBarPadding: Dp,
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
    switchColorPreset: (SettingsEvent.OnSwitchColorPreset) -> Unit,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit,
    leave: (ReaderEvent.OnLeave) -> Unit,
    restoreCheckpoint: (ReaderEvent.OnRestoreCheckpoint) -> Unit,
    scroll: (ReaderEvent.OnScroll) -> Unit,
    changeProgress: (ReaderEvent.OnChangeProgress) -> Unit,
    openShareApp: (ReaderEvent.OnOpenShareApp) -> Unit,
    openWebBrowser: (ReaderEvent.OnOpenWebBrowser) -> Unit,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openDictionary: (ReaderEvent.OnOpenDictionary) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    openImage: (ReaderEvent.OnOpenImage) -> Unit,
    dismissImage: (ReaderEvent.OnDismissImage) -> Unit,
    scrollToChapter: (ReaderEvent.OnScrollToChapter) -> Unit,
    showSettingsBottomSheet: (ReaderEvent.OnShowSettingsBottomSheet) -> Unit,
    dismissBottomSheet: (ReaderEvent.OnDismissBottomSheet) -> Unit,
    showChaptersDrawer: (ReaderEvent.OnShowChaptersDrawer) -> Unit,
    searchVisibility: (ReaderEvent.OnSearchVisibility) -> Unit,
    searchQueryChange: (ReaderEvent.OnSearchQueryChange) -> Unit,
    searchStep: (ReaderEvent.OnSearchStep) -> Unit,
    searchReturn: (ReaderEvent.OnSearchReturn) -> Unit,
    addToLibrary: (ReaderEvent.OnAddToLibrary) -> Unit,
    dismissDrawer: (ReaderEvent.OnDismissDrawer) -> Unit,
    navigateToBookInfo: (ReaderEvent.OnNavigateToBookInfo) -> Unit,
    navigateBack: (ReaderEvent.OnNavigateBack) -> Unit
) {
    ReaderBottomSheet(
        bottomSheet = bottomSheet,
        currentNote = currentNote,
        dismissBottomSheet = dismissBottomSheet
    )

    if (isLoading || errorMessage == null) {
        ReaderScaffold(
            book = book,
            text = text,
            listState = listState,
            currentChapter = currentChapter,
            nestedScrollConnection = nestedScrollConnection,
            fastColorPresetChange = fastColorPresetChange,
            perceptionExpander = perceptionExpander,
            perceptionExpanderPadding = perceptionExpanderPadding,
            perceptionExpanderThickness = perceptionExpanderThickness,
            horizontalLimiter = horizontalLimiter,
            horizontalLimiterHeight = horizontalLimiterHeight,
            horizontalLimiterVerticalOffset = horizontalLimiterVerticalOffset,
            horizontalLimiterRuler = horizontalLimiterRuler,
            horizontalLimiterRulerThickness = horizontalLimiterRulerThickness,
            horizontalLimiterDimming = horizontalLimiterDimming,
            currentChapterProgress = currentChapterProgress,
            isLoading = isLoading,
            checkpoints = checkpoints,
            search = search,
            showMenu = showMenu,
            coveredByBottomSheet = bottomSheet == ReaderScreen.SETTINGS_BOTTOM_SHEET,
            lockMenu = lockMenu,
            contentPadding = contentPadding,
            verticalPadding = verticalPadding,
            horizontalGesture = horizontalGesture,
            horizontalGestureScroll = horizontalGestureScroll,
            horizontalGestureSensitivity = horizontalGestureSensitivity,
            horizontalGestureAlphaAnim = horizontalGestureAlphaAnim,
            horizontalGesturePullAnim = horizontalGesturePullAnim,
            horizontalGestureDisableScrolling = horizontalGestureDisableScrolling,
            tapPaging = tapPaging,
            pageTurnAnimation = pageTurnAnimation,
            highlightedReading = highlightedReading,
            highlightedReadingThickness = highlightedReadingThickness,
            progress = progress,
            progressBar = progressBar,
            progressBarPadding = progressBarPadding,
            progressBarAlignment = progressBarAlignment,
            progressBarFontSize = progressBarFontSize,
            paragraphHeight = paragraphHeight,
            sidePadding = sidePadding,
            bottomBarPadding = bottomBarPadding,
            backgroundColor = backgroundColor,
            fontColor = fontColor,
            images = images,
            imagesCaptions = imagesCaptions,
            imagesCornersRoundness = imagesCornersRoundness,
            imagesAlignment = imagesAlignment,
            imagesWidth = imagesWidth,
            imagesColorEffects = imagesColorEffects,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            fontThickness = fontThickness,
            fontStyle = fontStyle,
            chapterTitleAlignment = chapterTitleAlignment,
            textAlignment = textAlignment,
            horizontalAlignment = horizontalAlignment,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            paragraphIndentation = paragraphIndentation,
            doubleClickTranslation = doubleClickTranslation,
            switchColorPreset = switchColorPreset,
            menuVisibility = menuVisibility,
            leave = leave,
            restoreCheckpoint = restoreCheckpoint,
            scroll = scroll,
            changeProgress = changeProgress,
            openShareApp = openShareApp,
            openWebBrowser = openWebBrowser,
            openTranslator = openTranslator,
            openNote = openNote,
            openImage = openImage,
            openDictionary = openDictionary,
            showSettingsBottomSheet = showSettingsBottomSheet,
            showChaptersDrawer = showChaptersDrawer,
            searchVisibility = searchVisibility,
            searchQueryChange = searchQueryChange,
            searchStep = searchStep,
            searchReturn = searchReturn,
            addToLibrary = addToLibrary,
            navigateBack = navigateBack,
            navigateToBookInfo = navigateToBookInfo
        )
    } else {
        ReaderErrorPlaceholder(
            errorMessage = errorMessage,
            leave = leave,
            navigateToBookInfo = navigateToBookInfo,
            navigateBack = navigateBack
        )
    }

    ReaderDrawer(
        drawer = drawer,
        chapters = remember(text) { text.filterIsInstance<Chapter>() },
        currentChapter = currentChapter,
        currentChapterProgress = currentChapterProgress,
        scrollToChapter = scrollToChapter,
        dismissDrawer = dismissDrawer
    )

    ReaderBackHandler(
        searchActive = search.active,
        searchReturn = searchReturn,
        leave = leave,
        navigateBack = navigateBack
    )

    // Last on purpose, on both counts: it draws over the reader and its menu,
    // and its back handler has to be registered after [ReaderBackHandler] so
    // that closing the viewer wins over leaving the book.
    fullscreenImage?.let { image ->
        ReaderImageViewer(
            entry = image,
            dismiss = { dismissImage(ReaderEvent.OnDismissImage) }
        )
    }
}