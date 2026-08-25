/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.core.ui.UIText

@Parcelize
@Immutable
data class Book(
    val id: Int = 0,

    val title: String,
    val author: UIText,
    val description: String?,

    val filePath: String,
    val coverImage: Uri?,

    val scrollIndex: Int,
    val scrollOffset: Int,
    val progress: Float,

    val lastOpened: Long?,
    val categories: List<Int>,

    /**
     * False while the book is only being previewed from a file manager. The
     * reader treats such a book as a book in every way but two: it records no
     * statistics for it, and it offers to add it.
     */
    val inLibrary: Boolean = true,

    /** How a preview reaches its file; see the entity of the same name. */
    val previewUri: String? = null,

    /** What identifies this book to its provider; see the entity of the same name. */
    val documentAuthority: String? = null,
    val documentId: String? = null
) : Parcelable {
    companion object {
        val default = Book(
            id = -1,
            title = "",
            author = UIText.StringValue(""),
            description = null,
            filePath = "",
            coverImage = null,
            scrollIndex = 0,
            scrollOffset = 0,
            progress = 0f,
            lastOpened = null,
            categories = emptyList()
        )
    }
}