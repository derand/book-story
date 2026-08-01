/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import android.app.Application
import android.graphics.BitmapFactory
import ua.acclorite.book_story.core.helpers.mapCatchingCancellable
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import javax.inject.Inject

private const val TAG = "CanResetCover"

class CanResetCoverImageUseCase @Inject constructor(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val coverImageHandler: CoverImageHandler
) {

    suspend operator fun invoke(bookId: Int): Boolean {
        logI(TAG, "Checking if can reset cover image of [$bookId].")

        bookRepository.getBook(bookId).mapCatchingCancellable { book ->
            // Getting default cover image
            val defaultCoverImage = bookRepository.getDefaultCover(book).getOrThrow()
                ?: return@mapCatchingCancellable false

            // Return true if current cover is null (and default is not)
            if (book.coverImage == null) {
                return@mapCatchingCancellable true
            }

            // Getting compressed cover images
            val compressedDefaultCoverImage =
                coverImageHandler.compressCover(defaultCoverImage).getOrThrow()
            val compressedCoverImage = application.contentResolver
                .openInputStream(book.coverImage)
                ?.use { BitmapFactory.decodeStream(it) }

            !compressedDefaultCoverImage.sameAs(compressedCoverImage)
        }.fold(
            onSuccess = {
                logI(TAG, "Can reset cover image of [$bookId]: $it.")
                return it
            },
            onFailure = {
                logE(
                    TAG,
                    "Could not check if can reset cover image of [$bookId] with error: ${it.message}"
                )
                return false
            }
        )
    }
}