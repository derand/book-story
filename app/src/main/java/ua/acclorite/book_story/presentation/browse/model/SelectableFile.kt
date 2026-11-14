/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse.model

import androidx.compose.runtime.Immutable

@Immutable
/** A file Browse lists: only files, never a folder. */
data class SelectableFile(
    val name: String,
    val uri: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val selected: Boolean
)