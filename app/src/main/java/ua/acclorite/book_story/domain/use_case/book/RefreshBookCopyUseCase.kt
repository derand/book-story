/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "RefreshBookCopy"

/**
 * Takes the app's copy of [book] from its source again.
 *
 * A copy cannot notice its original changing: once stored, the book is found by
 * a path in the app's own directory and nothing consults the source again. That
 * is the price of not depending on it, and this is what pays it — on request,
 * never on a guess.
 *
 * The reading position is left where it is. The book is the same book; if its
 * text moved, the bookmark moved with the text, and pretending to know by how
 * much would be worse than leaving it.
 *
 * Returns the refreshed book, or null when the source could not be read — in
 * which case the copy it has is untouched.
 */
class RefreshBookCopyUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(book: Book): Book? {
        val current = bookRepository.getBook(book.id).getOrElse {
            logW(TAG, "Could not read [${book.title}] back: ${it.message}")
            return null
        }

        if (!current.isOwnCopy || current.originPath == null) {
            logI(TAG, "[${current.title}] has nothing to refresh from.")
            return null
        }

        val path = bookRepository.refreshBookFile(current).getOrElse {
            logW(TAG, "Could not refresh [${current.title}]: ${it.message}")
            return null
        }

        val refreshed = current.copy(filePath = path)
        bookRepository.updateBook(refreshed).onFailure {
            logW(TAG, "Could not point [${current.title}] at the new copy: ${it.message}")
            return null
        }

        logI(TAG, "[${current.title}] was taken from its source again.")
        return refreshed
    }
}
