/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.presentation.reader.model.Checkpoint

@Immutable
sealed class ReaderEvent {

    data object OnLoadText : ReaderEvent()

    /**
     * Loads the images the text does not carry bytes for (a parse-cache hit) in
     * the background. Idempotent: a no-op while a load is running or once every
     * image is available. Dispatched only when images are shown at all.
     */
    data object OnLoadImages : ReaderEvent()

    data object OnRestoreScroll : ReaderEvent()

    data class OnMenuVisibility(
        val show: Boolean,
        val saveCheckpoint: Boolean
    ) : ReaderEvent()

    data class OnChangeProgress(
        val progress: Float,
        val firstVisibleItemIndex: Int,
        val firstVisibleItemOffset: Int
    ) : ReaderEvent()

    data class OnUpdateChapter(
        val index: Int
    ) : ReaderEvent()

    data class OnScrollToChapter(
        val chapter: Chapter
    ) : ReaderEvent()

    data class OnScroll(
        val progress: Float
    ) : ReaderEvent()

    data class OnRestoreCheckpoint(
        val checkpoint: Checkpoint
    ) : ReaderEvent()

    data class OnLeave(
        val navigate: () -> Unit
    ) : ReaderEvent()

    data class OnOpenTranslator(
        val textToTranslate: String,
        val translateWholeParagraph: Boolean
    ) : ReaderEvent()

    data class OnOpenShareApp(
        val textToShare: String
    ) : ReaderEvent()

    data class OnOpenWebBrowser(
        val textToSearch: String
    ) : ReaderEvent()

    data class OnOpenDictionary(
        val textToDefine: String
    ) : ReaderEvent()

    data object OnShowSettingsBottomSheet : ReaderEvent()

    /** [tag] is a clickable reference tag, e.g. "note:<id>" or "anchor:<id>". */
    data class OnOpenNote(
        val tag: String
    ) : ReaderEvent()

    /**
     * Opens [image] full screen, where it can be zoomed and panned. Dispatched
     * by a tap on the image itself, and only once its bytes are loaded — there
     * is nothing to show for a placeholder.
     */
    data class OnOpenImage(
        val image: ReaderText.Image
    ) : ReaderEvent()

    data object OnDismissImage : ReaderEvent()

    data object OnDismissBottomSheet : ReaderEvent()

    data object OnShowChaptersDrawer : ReaderEvent()

    data object OnDismissDrawer : ReaderEvent()

    data object OnNavigateBack : ReaderEvent()

    data class OnNavigateToBookInfo(
        val changePath: Boolean
    ) : ReaderEvent()

    /** Keep the book being previewed; see `AddPreviewToLibraryUseCase`. */
    data object OnAddToLibrary : ReaderEvent()

    /**
     * A folder the user has just granted, in answer to
     * [ReaderEffect.OnRequestFolderGrant]. Adding is retried with it.
     */
    data class OnGrantFolder(
        val uri: String
    ) : ReaderEvent()
}