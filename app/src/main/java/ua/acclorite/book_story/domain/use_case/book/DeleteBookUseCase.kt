/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import javax.inject.Inject

private const val TAG = "DeleteBook"

/**
 * Deletes a book: the database first, in one transaction, and its files only
 * once that has committed.
 *
 * The order is what makes an interrupted delete harmless. A file left behind
 * costs some storage; a row left behind without its file costs the book, and
 * for a kept copy there is nowhere else to take the file from again.
 */
class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val coverImageHandler: CoverImageHandler
) {

    /**
     * Not cancellable: it is started from screens the user can leave, and a
     * delete cut short between the commit and the file deletes would leave
     * those files behind for the life of the install.
     */
    suspend operator fun invoke(book: Book) = withContext(NonCancellable) {
        logI(TAG, "Deleting [${book.id}].")

        // History, coverage and the row go; sessions and the finished-book
        // record are unlinked, so the lifetime statistics keep them.
        val cover = bookRepository.deleteBookRecords(bookId = book.id).getOrElse {
            logE(TAG, "Could not delete [${book.id}] with error: ${it.messageForLog()}")
            return@withContext
        }

        cover?.let { coverImageHandler.deleteCover(it) }?.onFailure {
            logW(TAG, "Could not delete cover image with error: ${it.messageForLog()}")
        }

        // The app's own copy of the file, for a book that was kept by keeping
        // it. Nothing else refers to it, so it would otherwise sit in filesDir
        // for the life of the install.
        bookRepository.deleteBookFile(bookId = book.id).onFailure {
            logW(TAG, "Could not delete the stored file of [${book.id}]: ${it.messageForLog()}")
        }

        bookRepository.dropBookImages(bookId = book.id).onFailure {
            logW(TAG, "Could not drop images of [${book.id}]: ${it.messageForLog()}")
        }

        logI(TAG, "Successfully deleted [${book.id}].")
    }
}
