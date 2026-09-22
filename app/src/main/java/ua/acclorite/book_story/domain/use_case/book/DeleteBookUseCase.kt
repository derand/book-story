/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.repository.HistoryRepository
import ua.acclorite.book_story.domain.repository.StatisticsRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import javax.inject.Inject

private const val TAG = "DeleteBook"

class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val historyRepository: HistoryRepository,
    private val statisticsRepository: StatisticsRepository,
    private val coverImageHandler: CoverImageHandler
) {

    suspend operator fun invoke(book: Book) {
        logI(TAG, "Deleting [${book.id}].")

        // Deleting cover image
        book.coverImage?.let { coverImageHandler.deleteCover(it) }?.onFailure {
            logW(TAG, "Could not delete cover image with error: ${it.messageForLog()}")
        }

        // Deleting history
        historyRepository.deleteHistoryForBook(bookId = book.id).onFailure {
            logW(TAG, "Could not delete history for [${book.id}] with error: ${it.messageForLog()}")
        }

        // Unlinking, not deleting: the book's reading sessions stay in the
        // lifetime statistics, they just stop being attributable to it.
        statisticsRepository.anonymiseBookSessions(bookId = book.id).onFailure {
            logW(TAG, "Could not anonymise sessions of [${book.id}]: ${it.messageForLog()}")
        }

        // Coverage does go: its item indices mean nothing without the text.
        statisticsRepository.deleteCoverage(bookId = book.id).onFailure {
            logW(TAG, "Could not delete coverage of [${book.id}]: ${it.messageForLog()}")
        }

        // The book's own record stays on the shelf, just without the book.
        statisticsRepository.unlinkReadBook(bookId = book.id).onFailure {
            logW(TAG, "Could not unlink record of [${book.id}]: ${it.messageForLog()}")
        }

        // The app's own copy of the file, for a book that was kept by keeping
        // it. Nothing else refers to it, so it would otherwise sit in filesDir
        // for the life of the install.
        bookRepository.deleteBookFile(bookId = book.id).onFailure {
            logW(TAG, "Could not delete the stored file of [${book.id}]: ${it.messageForLog()}")
        }

        // Deleting book
        bookRepository.deleteBook(book).fold(
            onSuccess = {
                logI(TAG, "Successfully deleted [${book.id}].")
            },
            onFailure = {
                logE(TAG, "Could not delete [${book.id}] with error: ${it.messageForLog()}")
            }
        )
    }
}