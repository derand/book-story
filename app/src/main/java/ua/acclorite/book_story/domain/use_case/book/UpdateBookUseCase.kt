/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "UpdateBook"

/**
 * The changes a screen makes to a book, one field at a time: each writes only
 * what it names, never a screen's copy of the whole book. Each returns whether
 * the write happened, so that a screen says so only when it did.
 */
class UpdateBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /** On every settled scroll, so a success is not logged. */
    suspend fun position(bookId: Int, scrollIndex: Int, scrollOffset: Int, progress: Float) =
        bookRepository.updatePosition(bookId, scrollIndex, scrollOffset, progress)
            .logged(bookId, "position", quiet = true)

    suspend fun title(bookId: Int, title: String) =
        bookRepository.updateTitle(bookId, title).logged(bookId, "title")

    suspend fun author(bookId: Int, author: String) =
        bookRepository.updateAuthor(bookId, author).logged(bookId, "author")

    suspend fun description(bookId: Int, description: String?) =
        bookRepository.updateDescription(bookId, description).logged(bookId, "description")

    suspend fun filePath(bookId: Int, filePath: String) =
        bookRepository.updateFilePath(bookId, filePath).logged(bookId, "path")

    suspend fun categories(bookIds: List<Int>, categories: List<Int>) =
        bookRepository.updateCategories(bookIds, categories)
            .logged(bookIds.joinToString(), "categories")

    private fun Result<Unit>.logged(book: Any, what: String, quiet: Boolean = false): Boolean =
        fold(
            onSuccess = {
                if (!quiet) logI(TAG, "Updated the $what of [$book].")
                true
            },
            onFailure = {
                logE(TAG, "Could not update the $what of [$book]: ${it.messageForLog()}")
                false
            }
        )
}
