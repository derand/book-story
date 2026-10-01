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
 * - a caller that [statesIdentity] has just asked the provider, and what it
 *   carries is written, an empty identity included;
 * - any other caller is holding a copy of the row, loaded at some point before
 *   this write, so what it carries is old news and what is stored stays.
 *
 * The last two cannot be told apart from the book alone. The reader's copy is
 * loaded before the open that learns the identity, and writes back on every
 * save of the position: as long as an id it carried counted as stated, a stale
 * one overwrote the one just learned, on every open. Nothing failed — the book
 * was found again by its path, a listing per level — so only a changed id, as
 * after a restore on another device, ever showed the cost.
 */
internal fun identityToWrite(
    incoming: BookIdentity,
    stored: BookIdentity?,
    statesIdentity: Boolean = false
): BookIdentity = when {
    stored == null -> incoming
    stored.filePath != incoming.filePath -> incoming.copy(
        documentAuthority = null,
        documentId = null
    )
    statesIdentity -> incoming
    else -> incoming.copy(
        documentAuthority = stored.documentAuthority,
        documentId = stored.documentId
    )
}
