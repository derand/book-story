/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.bookSearchTokens
import ua.acclorite.book_story.domain.model.library.matchesSearch
import ua.acclorite.book_story.domain.repository.BookRepository
import javax.inject.Inject

private const val TAG = "ObserveLibraryBooks"

class ObserveLibraryBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /**
     * The library narrowed to [query] (blank: every book), again after each
     * change to the books, their history or the query. A failed read leaves an
     * empty list.
     */
    operator fun invoke(query: Flow<String>): Flow<List<Book>> =
        combine(
            bookRepository.observeLibraryBooks(),
            query.map { bookSearchTokens(it) }
        ) { allBooks, tokens ->
            allBooks.filter { book -> book.matchesSearch(tokens) }.also {
                logI(TAG, "Found [${it.size}] of [${allBooks.size}] books.")
            }
        }.catch {
            logE(TAG, "Could not read the library: ${it.messageForLog()}")
            emit(emptyList())
        }
}
