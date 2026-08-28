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

private const val TAG = "StoreBookCopy"

/**
 * Puts a copy of [book]'s file in the app's own storage and points the book at
 * it, so opening it never asks the source again.
 *
 * The source is left alone — this copies, it does not move — and the book keeps
 * everything else: its id, its reading position, its statistics. What changes
 * is where the bytes are read from.
 *
 * The identity is moved rather than dropped: `documentAuthority`/`documentId`
 * have to be cleared, because from here on the book is found by its path in the
 * app's directory, and they are written to the origin instead, which is what
 * makes the copy reversible and refreshable.
 *
 * Returns the stored book, or null when it could not be stored — in which case
 * the book is left exactly as it was, still reading its original in place. A
 * failed copy must not cost the user the book.
 */
class StoreBookCopyUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(book: Book): Book? {
        // The row is re-read rather than trusted: what a caller holds was true
        // when it was handed over, and this writes the *whole* row back. A book
        // that was just inserted has already gained a cover the caller's copy
        // does not know about, and writing that copy back would erase it.
        val current = bookRepository.getBook(book.id).getOrElse {
            logW(TAG, "Could not read [${book.title}] back: ${it.message}")
            return null
        }

        if (current.isOwnCopy) {
            logI(TAG, "[${current.title}] is already a copy.")
            return current
        }

        val path = bookRepository.storeBookFile(current).getOrElse {
            logW(TAG, "Could not store [${current.title}]: ${it.message}")
            return null
        }

        val stored = current.copy(
            filePath = path,
            documentAuthority = null,
            documentId = null,
            originAuthority = current.documentAuthority,
            originDocumentId = current.documentId,
            originPath = current.filePath
        )

        bookRepository.updateBook(stored).onFailure {
            logW(TAG, "Could not point [${current.title}] at its copy: ${it.message}")
            // The row still names the original, so the copy is unreachable and
            // would be a leak of the whole book's size.
            bookRepository.deleteBookFile(current.id)
            return null
        }

        logI(TAG, "[${current.title}] is now read from the app's own copy.")
        return stored
    }
}
