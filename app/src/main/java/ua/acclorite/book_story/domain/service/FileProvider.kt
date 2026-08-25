/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.service

import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.domain.model.library.Book

interface FileProvider {
    fun getFileFromBook(book: Book): Result<CachedFile>
    fun getStorageFiles(): Result<List<CachedFile>>

    /**
     * Every place the user granted, whether or not it answers — which is the
     * difference between this and [getStorageFiles], where a source that says
     * nothing is indistinguishable from an empty folder and is dropped.
     */
    fun getStorageSources(): Result<List<BookSource>>
}