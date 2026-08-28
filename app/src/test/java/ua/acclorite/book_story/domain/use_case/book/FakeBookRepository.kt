/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.repository.BookRepository

/**
 * A book repository that records what was written to it. Only the handful of
 * calls the preview use cases make are answered; anything else fails loudly
 * rather than returning a plausible empty value, so a test that starts reaching
 * further says so instead of quietly passing.
 */
class FakeBookRepository(
    private val books: List<Book> = emptyList(),
    private val previews: List<Book> = emptyList(),
    private val storedPath: String? = null
) : BookRepository {

    var updated: Book? = null
        private set

    var deleted: Book? = null
        private set

    /** The book whose file was copied into the app's own storage, if any. */
    var storedFor: Book? = null
        private set

    /** The book whose copy was given up, if any. */
    var releasedFor: Book? = null
        private set

    /** Set to make releasing succeed with this path; null makes it fail. */
    var releasedPath: String? = null

    override suspend fun getLibraryBooks(): Result<List<Book>> = Result.success(books)

    override suspend fun findPreviews(): Result<List<Book>> = Result.success(previews)

    override suspend fun updateBook(book: Book): Result<Unit> {
        updated = book
        return Result.success(Unit)
    }

    override suspend fun deleteBook(book: Book): Result<Unit> {
        deleted = book
        return Result.success(Unit)
    }

    override suspend fun dropBookImages(bookId: Int): Result<Unit> = Result.success(Unit)

    override suspend fun storeBookFile(book: Book): Result<String> {
        storedFor = book
        return storedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not store it."))
    }

    override suspend fun deleteBookFile(bookId: Int): Result<Unit> = Result.success(Unit)

    /** Set to make refreshing succeed with this path; null makes it fail. */
    var refreshedPath: String? = null

    /** The book whose copy was taken again from its source, if any. */
    var refreshedFor: Book? = null
        private set

    override suspend fun refreshBookFile(book: Book): Result<String> {
        refreshedFor = book
        return refreshedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not refresh it."))
    }

    override suspend fun releaseBookFile(book: Book): Result<String> {
        releasedFor = book
        return releasedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not release it."))
    }

    private fun unused(name: String): Nothing =
        throw UnsupportedOperationException("$name is not part of this fake.")

    override suspend fun getBook(bookId: Int): Result<Book> =
        (books + previews).firstOrNull { it.id == bookId }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("No book [$bookId]."))

    override suspend fun getText(bookId: Int): Result<ParsedText> = unused("getText")

    override suspend fun loadBookImages(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray>,
        onImage: (src: String, image: BookImage.Ready) -> Unit
    ): Result<Unit> = unused("loadBookImages")

    override suspend fun keepOnlyBookImages(bookId: Int): Result<Unit> =
        unused("keepOnlyBookImages")

    override suspend fun getFileFromBook(bookId: Int): Result<File> = unused("getFileFromBook")

    override suspend fun addBook(book: Book): Result<Int> = unused("addBook")

    override suspend fun findLibraryBookForFile(
        filePath: String,
        fileName: String,
        documentAuthority: String?,
        documentId: String?
    ): Result<Book?> = unused("findLibraryBookForFile")

    override suspend fun getDefaultCover(book: Book): Result<CoverImage?> =
        unused("getDefaultCover")
}
