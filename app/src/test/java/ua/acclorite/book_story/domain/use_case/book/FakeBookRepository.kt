/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import android.net.Uri
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

    /** What updating a book answers. */
    var updateResult: Result<Unit> = Result.success(Unit)

    /**
     * The calls the copy use cases make, in order: `store`, `refresh`,
     * `resolve`, `update`, `release`, `delete` and `keepOnly <path>`.
     */
    val copyCalls = mutableListOf<String>()

    /** Runs inside `storeBookFile`, before it answers. */
    var onStore: suspend () -> Unit = {}

    /**
     * The delete calls, in the order they were made: `records`, `file` and
     * `images`, each with the book id. Order is the point of a delete.
     */
    val deleteCalls = mutableListOf<String>()

    /** What deleting a book's records answers: the cover its row named. */
    var deleteRecordsResult: Result<Uri?> = Result.success(null)

    /** Runs inside `deleteBookRecords`, before it answers. */
    var onDeleteRecords: suspend () -> Unit = {}

    /** The book whose file was copied into the app's own storage, if any. */
    var storedFor: Book? = null
        private set

    /** The book whose copy was given up, if any. */
    var releasedFor: Book? = null
        private set

    /** Set to make resolving the original succeed with this path; null makes it fail. */
    var releasedPath: String? = null

    override suspend fun getLibraryBooks(): Result<List<Book>> = Result.success(books)

    override suspend fun findPreviews(): Result<List<Book>> = Result.success(previews)

    /** Records the book whose location was written, as `updated`. */
    override suspend fun updateLocation(book: Book): Result<Unit> {
        copyCalls += "update"
        if (updateResult.isFailure) return updateResult
        updated = book
        return updateResult
    }

    override suspend fun deleteBookRecords(bookId: Int): Result<Uri?> {
        onDeleteRecords()
        deleteCalls += "records $bookId"
        return deleteRecordsResult
    }

    override suspend fun dropBookImages(bookId: Int): Result<Unit> {
        deleteCalls += "images $bookId"
        return Result.success(Unit)
    }

    override suspend fun storeBookFile(book: Book): Result<String> {
        storedFor = book
        copyCalls += "store"
        onStore()
        return storedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not store it."))
    }

    override suspend fun deleteBookFile(bookId: Int): Result<Unit> {
        deleteCalls += "file $bookId"
        copyCalls += "delete"
        return Result.success(Unit)
    }

    override suspend fun keepOnlyBookFile(bookId: Int, path: String): Result<Unit> {
        copyCalls += "keepOnly $path"
        return Result.success(Unit)
    }

    /** Set to make refreshing succeed with this path; null makes it fail. */
    var refreshedPath: String? = null

    /** The book whose copy was taken again from its source, if any. */
    var refreshedFor: Book? = null
        private set

    override suspend fun refreshBookFile(book: Book): Result<String> {
        refreshedFor = book
        copyCalls += "refresh"
        return refreshedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not refresh it."))
    }

    override suspend fun resolveBookOrigin(book: Book): Result<String> {
        copyCalls += "resolve"
        return releasedPath?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Could not reach the original."))
    }

    override suspend fun releaseBookFile(book: Book): Result<Unit> {
        releasedFor = book
        copyCalls += "release"
        return Result.success(Unit)
    }

    private fun unused(name: String): Nothing =
        throw UnsupportedOperationException("$name is not part of this fake.")

    /** The row as last written, if [updateLocation] wrote one, else as given. */
    override suspend fun getBook(bookId: Int): Result<Book> =
        (listOfNotNull(updated) + books + previews).firstOrNull { it.id == bookId }
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

    override suspend fun updatePosition(
        bookId: Int,
        scrollIndex: Int,
        scrollOffset: Int,
        progress: Float
    ): Result<Unit> = unused("updatePosition")

    override suspend fun updateTitle(bookId: Int, title: String): Result<Unit> =
        unused("updateTitle")

    override suspend fun updateAuthor(bookId: Int, author: String): Result<Unit> =
        unused("updateAuthor")

    override suspend fun updateDescription(bookId: Int, description: String?): Result<Unit> =
        unused("updateDescription")

    override suspend fun updateCoverImage(bookId: Int, coverImage: Uri?): Result<Unit> =
        unused("updateCoverImage")

    override suspend fun updateCategories(
        bookIds: List<Int>,
        categories: List<Int>
    ): Result<Unit> = unused("updateCategories")

    override suspend fun updateFilePath(bookId: Int, filePath: String): Result<Unit> =
        unused("updateFilePath")
}
