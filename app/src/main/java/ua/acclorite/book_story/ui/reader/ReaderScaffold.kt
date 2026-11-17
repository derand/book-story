/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import android.annotation.SuppressLint
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.ui.common.components.common.AnimatedVisibility
import ua.acclorite.book_story.ui.reader.model.ReaderControls
import ua.acclorite.book_story.ui.reader.model.ReaderLook
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ReaderScaffold(
    book: Book,
    text: List<ReaderText>,
    listState: LazyListState,
    currentChapter: Chapter?,
    nestedScrollConnection: NestedScrollConnection,
    currentChapterProgress: Float,
    isLoading: Boolean,
    checkpoints: List<Checkpoint>,
    search: ReaderSearch,
    showMenu: Boolean,
    coveredByBottomSheet: Boolean,
    lockMenu: Boolean,
    contentPadding: PaddingValues,
    look: ReaderLook,
    controls: ReaderControls,
    progress: String,
    switchColorPreset: (SettingsEvent.OnSwitchColorPreset) -> Unit,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit,
    leave: (ReaderEvent.OnLeave) -> Unit,
    restoreCheckpoint: (ReaderEvent.OnRestoreCheckpoint) -> Unit,
    scroll: (ReaderEvent.OnScroll) -> Unit,
    dragProgress: (ReaderEvent.OnDragProgress) -> Unit,
    openShareApp: (ReaderEvent.OnOpenShareApp) -> Unit,
    openWebBrowser: (ReaderEvent.OnOpenWebBrowser) -> Unit,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    openImage: (ReaderEvent.OnOpenImage) -> Unit,
    showSettingsBottomSheet: (ReaderEvent.OnShowSettingsBottomSheet) -> Unit,
    showChaptersDrawer: (ReaderEvent.OnShowChaptersDrawer) -> Unit,
    searchVisibility: (ReaderEvent.OnSearchVisibility) -> Unit,
    searchQueryChange: (ReaderEvent.OnSearchQueryChange) -> Unit,
    searchStep: (ReaderEvent.OnSearchStep) -> Unit,
    searchReturn: (ReaderEvent.OnSearchReturn) -> Unit,
    addingToLibrary: Boolean,
    addToLibrary: (ReaderEvent.OnAddToLibrary) -> Unit,
    navigateToBookInfo: (ReaderEvent.OnNavigateToBookInfo) -> Unit,
    navigateBack: (ReaderEvent.OnNavigateBack) -> Unit
) {
    // How much of the page the preview bar is taking, so the text can end above
    // it. Zero for a book in the library, which has no such bar.
    var previewBarHeight by remember { mutableStateOf(0.dp) }
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Hidden while a sheet covers the screen, rather than switched off:
            // the bars under it cannot be reached anyway, but [showMenu] is what
            // the page tap toggles and what drives the system bars, so clearing it
            // here would both leave the reader menu gone once the sheet closes and
            // move the insets — resizing the sheet as it opens.
            AnimatedVisibility(
                visible = showMenu && !coveredByBottomSheet,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                ReaderTopBar(
                    book = book,
                    currentChapter = currentChapter,
                    fastColorPresetChange = controls.fastColorPresetChange,
                    currentChapterProgress = currentChapterProgress,
                    isLoading = isLoading,
                    lockMenu = lockMenu,
                    search = search,
                    listState = listState,
                    leave = leave,
                    switchColorPreset = switchColorPreset,
                    showSettingsBottomSheet = showSettingsBottomSheet,
                    showChaptersDrawer = showChaptersDrawer,
                    searchVisibility = searchVisibility,
                    searchQueryChange = searchQueryChange,
                    searchStep = searchStep,
                    searchReturn = searchReturn,
                    navigateBack = navigateBack,
                    navigateToBookInfo = navigateToBookInfo
                )
            }
        },
        bottomBar = {
            // A Column, because the slot holds two bars for a previewed book and
            // Scaffold measures its bottom bar as one: the preview bar sits below
            // the menu's rather than replacing it, so a previewed book is read
            // like any other and the progress slider stays where it always is.
            Column {
                AnimatedVisibility(
                    modifier = Modifier.fillMaxWidth(),
                    visible = showMenu && !coveredByBottomSheet,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    ReaderBottomBar(
                        book = book,
                        progress = progress,
                        text = text,
                        listState = listState,
                        lockMenu = lockMenu,
                        checkpoints = checkpoints,
                        bottomBarPadding = look.bottomBarPadding,
                        restoreCheckpoint = restoreCheckpoint,
                        scroll = scroll,
                        dragProgress = dragProgress
                    )
                }

                if (!book.inLibrary && !coveredByBottomSheet) {
                    ReaderPreviewBar(
                        addingToLibrary = addingToLibrary,
                        addToLibrary = addToLibrary,
                        onHeightChanged = { previewBarHeight = it }
                    )
                }
            }
        }
    ) {
        ReaderLayout(
            text = text,
            listState = listState,
            contentPadding = if (book.inLibrary) contentPadding else PaddingValues(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + previewBarHeight
            ),
            verticalPadding = look.verticalPadding,
            horizontalGesture = controls.horizontalGesture,
            horizontalGestureSensitivity = controls.horizontalGestureSensitivity,
            horizontalGestureAlphaAnim = controls.horizontalGestureAlphaAnim,
            horizontalGesturePullAnim = controls.horizontalGesturePullAnim,
            tapPaging = controls.tapPaging,
            volumePaging = controls.volumePaging,
            disableScrolling = controls.disableScrolling,
            pageTurnOverlap = controls.pageTurnOverlap,
            pageTurnAnimation = controls.pageTurnAnimation,
            highlightedReading = look.text.highlightedReading,
            highlightedReadingThickness = look.text.highlightedReadingThickness,
            progress = progress,
            progressBar = look.progressBar,
            progressBarPadding = look.progressBarPadding,
            progressBarAlignment = look.progressBarAlignment,
            progressBarFontSize = look.progressBarFontSize,
            chapterBreak = look.chapterBreak,
            paragraphHeight = look.paragraphHeight,
            sidePadding = look.text.sidePadding,
            backgroundColor = look.backgroundColor,
            fontColor = look.text.fontColor,
            images = look.images,
            imagesCaptions = look.text.imagesCaptions,
            imagesCornersRoundness = look.text.imagesCornersRoundness,
            imagesAlignment = look.text.imagesAlignment,
            imagesWidth = look.text.imagesWidth,
            imagesColorEffects = look.text.imagesColorEffects,
            fontFamily = look.text.fontFamily,
            lineHeight = look.text.lineHeight,
            fontThickness = look.text.fontThickness,
            fontStyle = look.text.fontStyle,
            chapterTitleAlignment = look.text.chapterTitleAlignment,
            textAlignment = look.text.textAlignment,
            horizontalAlignment = look.text.horizontalAlignment,
            fontSize = look.text.fontSize,
            letterSpacing = look.text.letterSpacing,
            paragraphIndentation = look.text.paragraphIndentation,
            doubleClickTranslation = controls.doubleClickTranslation,
            searchMatches = search.matches,
            currentSearchMatch = search.currentMatch,
            isLoading = isLoading,
            showMenu = showMenu,
            menuVisibility = menuVisibility,
            openShareApp = openShareApp,
            openWebBrowser = openWebBrowser,
            openTranslator = openTranslator,
            openNote = openNote,
            openImage = openImage
        )

        ReaderPerceptionExpander(
            perceptionExpander = look.perceptionExpander,
            perceptionExpanderPadding = look.perceptionExpanderPadding,
            perceptionExpanderThickness = look.perceptionExpanderThickness,
            perceptionExpanderColor = look.text.fontColor
        )

        ReaderHorizontalLimiter(
            horizontalLimiter = look.horizontalLimiter,
            horizontalLimiterHeight = look.horizontalLimiterHeight,
            horizontalLimiterVerticalOffset = look.horizontalLimiterVerticalOffset,
            horizontalLimiterRulerThickness = look.horizontalLimiterRulerThickness,
            horizontalLimiterRuler = look.horizontalLimiterRuler,
            horizontalLimiterDimming = look.horizontalLimiterDimming,
            horizontalLimiterDimmingColor = look.backgroundColor,
            horizontalLimiterRulerColor = look.text.fontColor
        )

        if (isLoading) {
            ReaderLoadingPlaceholder()
        }
    }
}