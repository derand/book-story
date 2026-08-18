/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.repository

import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ParsedText

interface BookRepository {
    /** Every book in the library; previews are not books yet and are left out. */
    suspend fun getLibraryBooks(): Result<List<Book>>

    suspend fun getBook(
        bookId: Int
    ): Result<Book>

    suspend fun getText(
        bookId: Int
    ): Result<ParsedText>

    /**
     * Resolves the images [srcs] of the open book, calling [onImage] for each one
     * as soon as it is available. Most arrive as files; the first few small ones
     * are kept in memory instead (see
     * [ua.acclorite.book_story.data.cache.ImageMemoryBudget]).
     *
     * [parsed] carries the bytes a fresh parse has just produced, keyed by src;
     * they are handed over like everything else, so the reader has a single render
     * path and the text itself never holds encoded bytes. It is empty for a book
     * restored from the parse cache, whose text carries image metadata only.
     */
    suspend fun loadBookImages(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray>,
        onImage: (src: String, image: BookImage.Ready) -> Unit
    ): Result<Unit>

    /**
     * Drops the transient image files of every book except [bookId]; call when a
     * reader opens. The book's own files are kept so that reopening it costs
     * nothing; the persistent parse-cache blobs are a different thing entirely.
     */
    suspend fun keepOnlyBookImages(bookId: Int): Result<Unit>

    /** Drops one book's image files, for a book that is being deleted. */
    suspend fun dropBookImages(bookId: Int): Result<Unit>

    /**
     * Copies [book]'s file into the app's own storage and returns where it
     * landed — for a book whose provider exposes no location, so there is
     * nothing to remember and no folder to ask the user for.
     */
    suspend fun storeBookFile(book: Book): Result<String>

    /** Drops the app's own copy of a book's file, if it has one. */
    suspend fun deleteBookFile(bookId: Int): Result<Unit>

    suspend fun getFileFromBook(
        bookId: Int
    ): Result<File>

    /** Returns the id of the inserted row, which is how a preview is opened. */
    suspend fun addBook(
        book: Book
    ): Result<Int>

    /**
     * The library book holding this file, if the user already has it — a file
     * arriving from a file manager should reach that book rather than become a
     * second copy of it.
     *
     * By path first. Failing that by file name, because a document URI does not
     * always yield a path, and because the same book reached through two
     * providers can carry two different ones. Size would be the better second
     * key and is not available: a book row does not store it.
     */
    suspend fun findLibraryBookForFile(
        filePath: String,
        fileName: String
    ): Result<Book?>

    /**
     * Books left mid-preview. By design a preview never outlives the process
     * that created it, so anything this returns was left by a crash.
     */
    suspend fun findPreviews(): Result<List<Book>>

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