/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.domain.model.reader.BookImage
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "LoadBookImages"

/**
 * Resolves the images of the open book, in the background, handing each one over
 * as soon as it is ready. Failures are not fatal: the text is already on screen,
 * an image that cannot be loaded simply stays unavailable.
 */
class LoadBookImagesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    suspend operator fun invoke(
        bookId: Int,
        srcs: Set<String>,
        parsed: Map<String, ByteArray> = emptyMap(),
        onImage: (src: String, image: BookImage.Ready) -> Unit
    ) {
        if (bookId == -1 || srcs.isEmpty()) return
        logI(TAG, "Loading [${srcs.size}] image(s) of [$bookId].")

        bookRepository.loadBookImages(bookId, srcs, parsed, onImage).fold(
            onSuccess = { logI(TAG, "Finished loading images of [$bookId].") },
            onFailure = { logE(TAG, "Could not load images: ${it.message}") }
        )
    }
}
