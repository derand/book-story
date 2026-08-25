/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.file

import androidx.compose.runtime.Immutable

/**
 * A place the user granted the app to read books from.
 *
 * The app had no name for this before, and said what it could not know instead:
 * Settings showed a path reconstructed from a document id, which only
 * `com.android.externalstorage` can be reconstructed for — a folder on Google
 * Drive came out as an empty name above `/storage/emulated/0`.
 *
 * Everything here is either given by the platform or asked of the provider
 * itself, and nothing is inferred from a path.
 */
@Immutable
data class BookSource(
    /** The granted tree, and what identifies this source to the app. */
    val uri: String,

    /**
     * The app holding it, by its own label — "Drive", "External Storage". Null
     * when the package is gone, which is a way a source dies.
     *
     * Not from `DocumentsContract.buildRootsUri`, which would be the documented
     * place for a name: reading it needs `ACTION_OPEN_DOCUMENT` and throws a
     * `SecurityException` for a tree grant, however the tree was granted.
     */
    val provider: String?,

    /**
     * The authority the grant names, which is there whether or not the app
     * behind it can be seen. Not a name for a person to read, and used only
     * where two sources that have both gone dark would otherwise be one unnamed
     * row twice over — and one of them may be the one to remove.
     */
    val authority: String?,

    /** What the provider calls the folder — "books", "books_story". */
    val name: String?,

    /**
     * The document id of the granted tree, which the grant carries and so
     * survives the provider going quiet — unlike [name], which has to be asked
     * for.
     *
     * Readable where the provider composes ids out of paths
     * (`primary:Download/books_story`) and opaque where it does not
     * (`acc=1;doc=encoded=…`). Shown only when there is nothing better, because
     * two folders that have both gone dark on one provider are otherwise the
     * same row twice over, and one of them may be the one to remove.
     */
    val treeDocumentId: String?,

    /**
     * Whether it answered when it was last asked.
     *
     * A grant outlives the thing it was granted on: an account removed, a folder
     * deleted, a provider uninstalled, and — for a while after every reboot — a
     * provider that is simply not up yet. None of that is the app's to control,
     * which is the argument for reporting it rather than hiding it.
     */
    val isAvailable: Boolean
)
