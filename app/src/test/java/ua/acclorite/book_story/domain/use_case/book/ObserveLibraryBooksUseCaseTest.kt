/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.book

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book

/** A library that cannot be read must not look like an empty one. */
@RunWith(RobolectricTestRunner::class)
class ObserveLibraryBooksUseCaseTest {

    private fun book(id: Int, title: String) = Book(
        id = id,
        title = title,
        author = UIText.StringValue("An author"),
        description = null,
        filePath = "/books/$id",
        coverImage = null,
        scrollIndex = 0,
        scrollOffset = 0,
        progress = 0f,
        lastOpened = null,
        categories = emptyList()
    )

    @Test
    fun `the whole library comes with the books the query matches`() = runBlocking {
        val books = listOf(book(1, "Dune"), book(2, "Emma"))
        val useCase = ObserveLibraryBooksUseCase(FakeBookRepository(books = books))

        val result = useCase(MutableStateFlow("dune")).first().getOrThrow()

        assertEquals(books, result.all)
        assertEquals(listOf(books[0]), result.matching)
    }

    @Test
    fun `a failed read ends in a failure, not an empty library`() = runBlocking {
        val failing = flow<List<Book>> { throw IllegalStateException("database closed") }
        val useCase = ObserveLibraryBooksUseCase(FakeBookRepository(library = failing))

        val result = useCase(MutableStateFlow("")).first()

        assertTrue(result.isFailure)
    }
}
