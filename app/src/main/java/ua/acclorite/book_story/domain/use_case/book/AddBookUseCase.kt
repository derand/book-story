/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import androidx.core.net.toUri
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.CoverImageHandler
import javax.inject.Inject

private const val TAG = "AddBook"

class AddBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val coverImageHandler: CoverImageHandler
) {

    /** The id of the inserted row, or null if it could not be inserted. */
    suspend operator fun invoke(book: Book, coverImage: CoverImage?): Int? {
        logI(TAG, "Inserting [${book.title}].")

        val coverImageUri = coverImage?.let { coverImage ->
            coverImageHandler.saveCover(coverImage).fold(
                onSuccess = {
                    logI(TAG, "Successfully saved cover image of [${book.title}].")
                    it.toUri()
                },
                onFailure = {
                    logW(TAG, "Could not save cover image with error: ${it.message}")
                    null
                }
            )
        }

        return bookRepository.addBook(book = book.copy(coverImage = coverImageUri)).fold(
            onSuccess = { id ->
                logI(TAG, "Successfully inserted [${book.title}].")
                id
            },
            onFailure = {
                logE(TAG, "Could not insert [${book.title}] with error: ${it.message}")
                null
            }
        )
    }
}