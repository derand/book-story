/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable

@Immutable
sealed class BookSourcesEvent {
    data class OnGrantPersistableUriPermission(
        val uri: String
    ) : BookSourcesEvent()

    data class OnReleasePersistableUriPermission(
        val uri: String
    ) : BookSourcesEvent()

    /** Re-asks every granted source whether it answers. */
    data object OnRefreshBookSources : BookSourcesEvent()
}
