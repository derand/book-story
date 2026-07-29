/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "ClearBookImages"

/**
 * Drops the transient image files written for a book while it was open. The
 * persistent parse-cache blobs are a different thing and are left alone.
 */
class ClearBookImagesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(bookId: Int) {
        if (bookId == -1) return
        bookRepository.clearBookImages(bookId).onFailure {
            logE(TAG, "Could not clear images of [$bookId]: ${it.message}")
        }
    }
}
