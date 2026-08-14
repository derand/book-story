/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book

interface FileSystemRepository {
    suspend fun searchFiles(
        query: String = ""
    ): Result<List<File>>

    suspend fun getBookFromFile(
        file: File
    ): Result<Pair<Book, CoverImage?>>

    /**
     * The file behind a URI handed over by another app. Its [File.path] is empty
     * when the providing app exposes no real path — which is not a failure, only
     * a file that cannot be matched against the library or added to it.
     */
    suspend fun getFileFromUri(
        uri: String
    ): Result<File>
}