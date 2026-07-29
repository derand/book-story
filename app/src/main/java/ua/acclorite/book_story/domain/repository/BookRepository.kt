/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.ParsedText

interface BookRepository {
    suspend fun searchBooks(
        query: String
    ): Result<List<Book>>

    suspend fun getBook(
        bookId: Int
    ): Result<Book>

    suspend fun getText(
        bookId: Int
    ): Result<ParsedText>

    /**
     * Resolves the images [srcs] of the open book to local files, calling
     * [onImage] for each one as soon as it is available.
     *
     * [parsed] carries the bytes a fresh parse has just produced, keyed by src;
     * they are written out and handed back as files like everything else, so the
     * reader has a single render path and never holds encoded bytes itself. It
     * is empty for a book restored from the parse cache, whose text carries
     * image metadata only.
     */
    suspend fun loadBookImages(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray>,
        onImage: (src: String, file: java.io.File) -> Unit
    ): Result<Unit>

    /** Drops the transient image files of [bookId]; call when its reader closes. */
    suspend fun clearBookImages(bookId: Int): Result<Unit>

    suspend fun getFileFromBook(
        bookId: Int
    ): Result<File>

    suspend fun addBook(
        book: Book
    ): Result<Unit>

    suspend fun updateBook(
        book: Book
    ): Result<Unit>

    suspend fun deleteBook(
        book: Book
    ): Result<Unit>

    suspend fun getDefaultCover(
        book: Book
    ): Result<CoverImage?>
}