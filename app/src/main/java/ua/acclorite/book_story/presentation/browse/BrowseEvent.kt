/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.presentation.browse.model.SelectableFile
import ua.acclorite.book_story.presentation.browse.model.SelectableNullableBook

@Immutable
sealed class BrowseEvent {
    data class OnRefreshList(
        val loading: Boolean,
        val hideSearch: Boolean
    ) : BrowseEvent()

    data class OnSearchVisibility(
        val show: Boolean
    ) : BrowseEvent()

    data object OnRequestFocus : BrowseEvent()

    data class OnSearchQueryChange(
        val query: String
    ) : BrowseEvent()

    data object OnSearch : BrowseEvent()

    data object OnClearSelectedFiles : BrowseEvent()

    data class OnSelectFiles(
        val files: List<SelectableFile>
    ) : BrowseEvent()

    data object OnShowFilterBottomSheet : BrowseEvent()

    data object OnDismissBottomSheet : BrowseEvent()

    data object OnShowAddDialog : BrowseEvent()

    data object OnDismissAddDialog : BrowseEvent()

    /**
     * Stop adding books, leaving the ones already added where they are.
     *
     * It takes effect between books, not inside one: copying a file is blocking
     * I/O with nowhere to notice, and a half-copied book in the library would be
     * worse than one book more than was wanted.
     */
    data object OnCancelAddingBooks : BrowseEvent()

    data class OnSelectAddDialog(
        val book: SelectableNullableBook
    ) : BrowseEvent()

    data object OnActionAddDialog : BrowseEvent()

    data object OnDismissDialog : BrowseEvent()

    data class OnUpdatePinnedPaths(
        val path: String
    ) : BrowseEvent()

    data object OnNavigateToLibrary : BrowseEvent()

    data object OnNavigateToBrowseSettings : BrowseEvent()
}