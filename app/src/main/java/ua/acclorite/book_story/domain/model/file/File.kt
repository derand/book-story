/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.file

import androidx.compose.runtime.Immutable

@Immutable
data class File(
    val name: String,
    val uri: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,

    /**
     * What the provider calls this document, when there is a provider behind it;
     * see [ua.acclorite.book_story.data.local.dto.BookEntity.documentId].
     */
    val documentAuthority: String? = null,
    val documentId: String? = null
)