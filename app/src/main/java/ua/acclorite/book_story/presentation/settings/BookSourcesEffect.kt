/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable

@Immutable
sealed class BookSourcesEffect {
    /** A folder could not be added ([added]) or removed; nothing changed. */
    data class OnSourceChangeFailed(
        val added: Boolean
    ) : BookSourcesEffect()
}
