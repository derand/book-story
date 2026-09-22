/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import android.net.Uri
import androidx.core.net.toUri
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.helpers.mapCatchingCancellable
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import javax.inject.Inject

private const val TAG = "UpdateCoverImage"

class UpdateCoverImageUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val coverImageHandler: CoverImageHandler
) {

    /**
     * The image the user picked, read before it replaces the cover.
     *
     * Reading it here rather than where it was picked is the whole point: it is
     * an arbitrary photo, it is decoded at the size a cover is drawn at, and
     * neither of those belongs in a picker's callback on the main thread.
     */
    suspend operator fun invoke(bookId: Int, coverUri: Uri) {
        val coverImage = coverImageHandler.decodeCover(coverUri).getOrElse {
            logE(TAG, "Could not read the chosen image for [$bookId]: ${it.messageForLog()}")
            return
        }
        invoke(bookId, coverImage)
    }

    suspend operator fun invoke(bookId: Int, coverImage: CoverImage?) {
        logI(TAG, "Updating cover image of [$bookId].")

        bookRepository.getBook(bookId).mapCatchingCancellable { book ->
            if (book.coverImage == coverImage) return

            // Deleting old cover
            book.coverImage?.let { coverImageHandler.deleteCover(it) }?.onFailure {
                logW(TAG, "Could not delete old cover image with error: ${it.messageForLog()}")
            }

            // Saving new cover
            val newCoverImage = coverImage?.let {
                coverImageHandler.saveCover(it).getOrThrow()
            }

            book.copy(coverImage = newCoverImage?.toUri())
        }.mapCatchingCancellable { newBook ->
            bookRepository.updateBook(newBook).getOrThrow()
        }.fold(
            onSuccess = {
                logI(TAG, "Successfully updated cover image of [$bookId].")
            },
            onFailure = {
                logE(TAG, "Could not update cover image of [$bookId] with error: ${it.messageForLog()}")
            }
        )
    }
}