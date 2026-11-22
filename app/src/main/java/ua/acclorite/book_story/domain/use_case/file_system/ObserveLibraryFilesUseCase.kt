/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.file_system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.library.LibraryFiles
import ua.acclorite.book_story.domain.model.library.libraryFiles
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "ObserveLibraryFiles"

class ObserveLibraryFilesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /**
     * The files the library holds, again whenever that changes — a book added,
     * deleted or pointed elsewhere — so Browse stops or starts offering them
     * without walking a folder. A failed read is the last value, a failure.
     */
    operator fun invoke(): Flow<Result<LibraryFiles>> =
        bookRepository.observeLibraryBooks()
            .map { books -> books.libraryFiles() }
            .distinctUntilChanged()
            .map { Result.success(it) }
            .catch {
                logE(TAG, "Could not read the library: ${it.messageForLog()}")
                emit(Result.failure(it))
            }
}
