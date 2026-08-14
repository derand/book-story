/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.runtime.Immutable

@Immutable
sealed class ReaderEffect {
    data class OnSystemBarsVisibility(
        val show: Boolean?
    ) : ReaderEffect()

    data object OnResetBrightness : ReaderEffect()

    data class OnOpenTranslator(
        val textToTranslate: String,
        val translateWholeParagraph: Boolean
    ) : ReaderEffect()

    data class OnOpenShareApp(
        val textToShare: String
    ) : ReaderEffect()

    data class OnOpenWebBrowser(
        val textToSearch: String
    ) : ReaderEffect()

    data class OnOpenDictionary(
        val textToDefine: String
    ) : ReaderEffect()

    data object OnNavigateBack : ReaderEffect()

    data class OnNavigateToBookInfo(
        val changePath: Boolean
    ) : ReaderEffect()

    /**
     * Nothing persisted reaches the previewed book's file, so it cannot be kept
     * until the user grants the folder holding it. [initialFolder] is where the
     * picker should open, when a path was recoverable.
     */
    data class OnRequestFolderGrant(
        val initialFolder: String?
    ) : ReaderEffect()

    data object OnAddedToLibrary : ReaderEffect()

    /** The file could not be read, so there was nothing to keep. */
    data object OnCannotAddToLibrary : ReaderEffect()
}