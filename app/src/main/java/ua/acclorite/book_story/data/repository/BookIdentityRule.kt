/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

/** Where a book is, and what the provider holding it calls it. */
internal data class BookIdentity(
    val filePath: String,
    val documentAuthority: String? = null,
    val documentId: String? = null
)

/**
 * The identity to store for a book being updated.
 *
 * The identity belongs to the **location**, and every rule here follows from
 * that one sentence:
 *
 * - a path that changed describes somewhere else, so no identity of the old
 *   place survives it — this is what re-pointing a book by hand does, and what
 *   keeping a previewed book as a copy of its file does;
 * - a caller that carries an identity is stating one, and it is written;
 * - a caller that carries none is not saying the book has none. It is holding a
 *   copy loaded before the identity was learned, which is what the reader holds
 *   on the very open that learned it, so what is stored stays.
 *
 * Only the first of those was wrong, and it was wrong in the direction where
 * nothing fails loudly: the path moved, the old id outlived it, and the book
 * went on opening the file it had always opened.
 */
internal fun identityToWrite(incoming: BookIdentity, stored: BookIdentity?): BookIdentity = when {
    stored == null -> incoming
    stored.filePath != incoming.filePath -> incoming.copy(
        documentAuthority = null,
        documentId = null
    )
    incoming.documentId != null -> incoming
    else -> incoming.copy(
        documentAuthority = stored.documentAuthority,
        documentId = stored.documentId
    )
}
