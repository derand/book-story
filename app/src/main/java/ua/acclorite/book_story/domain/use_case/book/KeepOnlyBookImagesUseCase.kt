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

private const val TAG = "KeepOnlyBookImages"

/**
 * Drops the transient image files of every book except the one being opened, whose
 * own files are kept — reopening a book should not have to extract its images all
 * over again. The persistent parse-cache blobs are a different thing and are left
 * alone.
 */
class KeepOnlyBookImagesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(bookId: Int) {
        if (bookId == -1) return
        bookRepository.keepOnlyBookImages(bookId).onFailure {
            logE(TAG, "Could not drop images of books other than [$bookId]: ${it.message}")
        }
    }
}
