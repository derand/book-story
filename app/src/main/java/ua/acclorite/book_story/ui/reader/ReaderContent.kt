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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.reader.model.ReaderVolumePaging
import ua.acclorite.book_story.presentation.settings.SettingsEvent
import ua.acclorite.book_story.ui.reader.model.ReaderControls
import ua.acclorite.book_story.ui.reader.model.ReaderLook

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
    currentChapterProgress: Float,
    isLoading: Boolean,
    errorMessage: UIText?,
    errorOffersPathChange: Boolean,
    checkpoints: List<Checkpoint>,
    search: ReaderSearch,
    showMenu: Boolean,
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
    dismissImage: (ReaderEvent.OnDismissImage) -> Unit,
    scrollToChapter: (ReaderEvent.OnScrollToChapter) -> Unit,
    showSettingsBottomSheet: (ReaderEvent.OnShowSettingsBottomSheet) -> Unit,
    dismissBottomSheet: (ReaderEvent.OnDismissBottomSheet) -> Unit,
    showChaptersDrawer: (ReaderEvent.OnShowChaptersDrawer) -> Unit,
    searchVisibility: (ReaderEvent.OnSearchVisibility) -> Unit,
    searchQueryChange: (ReaderEvent.OnSearchQueryChange) -> Unit,
    searchStep: (ReaderEvent.OnSearchStep) -> Unit,
    searchReturn: (ReaderEvent.OnSearchReturn) -> Unit,
    addingToLibrary: Boolean,
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
            currentChapterProgress = currentChapterProgress,
            isLoading = isLoading,
            checkpoints = checkpoints,
            search = search,
            showMenu = showMenu,
            coveredByBottomSheet = bottomSheet == ReaderScreen.SETTINGS_BOTTOM_SHEET,
            lockMenu = lockMenu,
            contentPadding = contentPadding,
            look = look,
            // Anything drawn over the reader takes the keys back with it. The
            // image viewer and a note's sheet both open with the menu hidden,
            // so the menu alone does not answer for them, and turning pages
            // under a picture the reader is looking at is a page turn nobody
            // sees — while the volume, which the overlay leaves alone, is what
            // the keys are expected to do there.
            controls = when {
                bottomSheet != null || drawer != null || fullscreenImage != null ->
                    controls.copy(volumePaging = ReaderVolumePaging.OFF)

                else -> controls
            },
            progress = progress,
            switchColorPreset = switchColorPreset,
            menuVisibility = menuVisibility,
            leave = leave,
            restoreCheckpoint = restoreCheckpoint,
            scroll = scroll,
            dragProgress = dragProgress,
            openShareApp = openShareApp,
            openWebBrowser = openWebBrowser,
            openTranslator = openTranslator,
            openNote = openNote,
            openImage = openImage,
            showSettingsBottomSheet = showSettingsBottomSheet,
            showChaptersDrawer = showChaptersDrawer,
            searchVisibility = searchVisibility,
            searchQueryChange = searchQueryChange,
            searchStep = searchStep,
            searchReturn = searchReturn,
            addingToLibrary = addingToLibrary,
            addToLibrary = addToLibrary,
            navigateBack = navigateBack,
            navigateToBookInfo = navigateToBookInfo
        )
    } else {
        ReaderErrorPlaceholder(
            errorMessage = errorMessage,
            offersPathChange = errorOffersPathChange,
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