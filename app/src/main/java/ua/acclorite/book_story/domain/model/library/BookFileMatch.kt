/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

/**
 * The book among these holding the given file, if any.
 *
 * By the document identity first, which is what a provider promises about a
 * file: unique to it, and durable. Where one of the two sides has none — a book
 * row not yet resolved since the identity was stored, a file the app owns a
 * copy of — the path decides, and then the file name.
 *
 * By file name last, because a file arriving from another app does not always
 * come with either — a document URI yields a path only when the providing app
 * exposes real storage. That match is deliberately loose, and it errs the safe
 * way: matching wrongly opens the user's own book instead of a preview, while
 * failing to match costs a parse and an extra row.
 */
fun List<Book>.findForFile(
    filePath: String,
    fileName: String,
    documentAuthority: String? = null,
    documentId: String? = null
): Book? {
    if (documentId != null) {
        firstOrNull {
            it.documentId == documentId && it.documentAuthority == documentAuthority
        }?.let { return it }
    }

    // Which books the looser matches are allowed to consider. A book identified
    // by the *same* provider has already had its chance above, and a different
    // id there is that provider saying these are different documents — no
    // likeness of path or name outvotes it. Two providers say nothing about one
    // another: the same file reached through each carries two unrelated ids, so
    // such a book stays a candidate, as does one that has no identity yet.
    val candidates = when (documentId) {
        null -> this
        else -> filter { it.documentId == null || it.documentAuthority != documentAuthority }
    }

    if (filePath.isNotBlank()) {
        candidates.firstOrNull { it.filePath == filePath }?.let { return it }
    }

    if (fileName.isBlank()) return null
    return candidates.firstOrNull { it.filePath.substringAfterLast('/') == fileName }
}
