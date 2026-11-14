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

/** The whole library, and the part of it a search narrows it to. */
data class LibraryBooks(
    val all: List<Book>,
    val matching: List<Book>
)

class ObserveLibraryBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /**
     * The library and its books matching [query] (blank: every book), again
     * after each change to the books, their history or the query. A failed
     * read leaves an empty library.
     */
    operator fun invoke(query: Flow<String>): Flow<LibraryBooks> =
        combine(
            bookRepository.observeLibraryBooks(),
            query.map { bookSearchTokens(it) }
        ) { allBooks, tokens ->
            val matching = allBooks.filter { book -> book.matchesSearch(tokens) }
            logI(TAG, "Found [${matching.size}] of [${allBooks.size}] books.")
            LibraryBooks(all = allBooks, matching = matching)
        }.catch {
            logE(TAG, "Could not read the library: ${it.messageForLog()}")
            emit(LibraryBooks(all = emptyList(), matching = emptyList()))
        }
}
