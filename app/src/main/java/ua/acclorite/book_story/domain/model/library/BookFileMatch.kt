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
 * By path first, because that is what identifies a book everywhere else in the
 * app. By file name second, because a file arriving from another app does not
 * always come with a path — a document URI yields one only when the providing
 * app exposes real storage — and because the same file reached through two
 * providers can carry two different paths.
 *
 * Size would be the better second key and is not available: a book row does not
 * store one. The name match is therefore deliberately loose, and it errs the
 * safe way: matching wrongly opens the user's own book instead of a preview,
 * while failing to match costs a parse and an extra row.
 */
fun List<Book>.findForFile(filePath: String, fileName: String): Book? {
    if (filePath.isNotBlank()) {
        firstOrNull { it.filePath == filePath }?.let { return it }
    }

    if (fileName.isBlank()) return null
    return firstOrNull { it.filePath.substringAfterLast('/') == fileName }
}
