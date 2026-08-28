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

private const val TAG = "ReleaseBookCopy"

/**
 * Gives up the app's own copy of [book] and goes back to reading the original
 * where it lives.
 *
 * The undo of [StoreBookCopyUseCase], and it can fail where storing could not:
 * the original may be gone, or its folder no longer granted. So the source is
 * asked to answer *before* anything is deleted, and if it will not, nothing is
 * — the copy is the only thing standing between the reader and a book they
 * cannot open.
 *
 * Returns the book reading its original again, or null if it is still reading
 * its copy.
 */
class ReleaseBookCopyUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(book: Book): Book? {
        val current = bookRepository.getBook(book.id).getOrElse {
            logW(TAG, "Could not read [${book.title}] back: ${it.message}")
            return null
        }

        if (!current.isOwnCopy) {
            logI(TAG, "[${current.title}] is not a copy.")
            return current
        }

        // The row is pointed back at the original first, and only then is the
        // copy deleted: a row updated over a deleted file is a book that cannot
        // be opened, while a copy outliving a failed update is a few megabytes.
        val path = bookRepository.releaseBookFile(current).getOrElse {
            logW(TAG, "Could not release [${current.title}]: ${it.message}")
            return null
        }

        val released = current.copy(
            filePath = path,
            documentAuthority = current.originAuthority,
            documentId = current.originDocumentId,
            originAuthority = null,
            originDocumentId = null,
            originPath = null
        )

        bookRepository.updateBook(released).onFailure {
            logW(TAG, "Could not point [${current.title}] back: ${it.message}")
            return null
        }

        logI(TAG, "[${current.title}] is read where it lives again.")
        return released
    }
}
