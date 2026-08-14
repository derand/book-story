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

private const val TAG = "DiscardPreviews"

/**
 * Throws away books that were only ever previewed.
 *
 * A preview is not meant to outlive the look the user took at it, so this runs
 * on two occasions: when they leave one without adding it, and at app start,
 * which is where the previews of a killed process are collected — nothing runs
 * on the way out of one.
 */
class DiscardPreviewsUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val deleteBookUseCase: DeleteBookUseCase
) {

    /** Discards [book] if, and only if, it is still a preview. */
    suspend fun discard(book: Book) {
        if (book.inLibrary) return

        logI(TAG, "Discarding preview [${book.title}].")
        deleteBookUseCase(book)
        bookRepository.dropBookImages(book.id).onFailure {
            logW(TAG, "Could not drop images of [${book.title}]: ${it.message}")
        }
    }

    /** Discards every preview there is. By design there never should be one. */
    suspend fun sweep() {
        val previews = bookRepository.findPreviews().getOrElse {
            logW(TAG, "Could not look for previews: ${it.message}")
            return
        }

        if (previews.isEmpty()) return
        logI(TAG, "Sweeping ${previews.size} preview(s) left by a killed process.")
        previews.forEach { discard(it) }
    }
}
