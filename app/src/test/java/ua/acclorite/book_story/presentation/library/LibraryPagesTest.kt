/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.Category
import ua.acclorite.book_story.presentation.library.model.LibraryPage
import ua.acclorite.book_story.presentation.library.model.LibrarySortOrder
import ua.acclorite.book_story.presentation.library.model.SelectableBook
import ua.acclorite.book_story.presentation.library.model.libraryPages
import ua.acclorite.book_story.presentation.library.model.pageAt

class LibraryPagesTest {

    private val default = Category(id = -1, title = "", order = -1)
    private val fiction = Category(id = 1, title = "Fiction", order = 0)
    private val poetry = Category(id = 2, title = "Poetry", order = 1)

    private fun book(id: Int, title: String, vararg categories: Int, lastOpened: Long? = null) =
        Book(
            id = id,
            title = title,
            author = UIText.StringValue("An author"),
            description = null,
            filePath = "/books/$id",
            coverImage = null,
            scrollIndex = 0,
            scrollOffset = 0,
            progress = 0f,
            lastOpened = lastOpened,
            categories = categories.toList()
        )

    private fun pages(
        library: List<Book>,
        shown: List<Book> = library,
        categories: List<Category> = listOf(poetry, default, fiction),
        alwaysShowDefault: Boolean = false,
        perCategorySort: Boolean = false,
        sortOrder: LibrarySortOrder = LibrarySortOrder.NAME
    ): List<LibraryPage> = libraryPages(
        categories = categories,
        library = library,
        shown = shown.map { SelectableBook(it, selected = false) },
        alwaysShowDefault = alwaysShowDefault,
        perCategorySort = perCategorySort,
        sortOrder = sortOrder,
        sortOrderDescending = false,
        defaultTitle = "Default"
    )

    private fun List<LibraryPage>.summary() =
        map { page -> page.title to page.books.map { it.data.title } }

    @Test
    fun `pages follow the category order, Default first, each with its books`() {
        val library = listOf(
            book(1, "b", 1), book(2, "a", 1, 2), book(3, "loose"), book(4, "stale", 9)
        )

        assertEquals(
            listOf(
                "Default" to listOf("loose", "stale"),
                "Fiction" to listOf("a", "b"),
                "Poetry" to listOf("a")
            ),
            pages(library).summary()
        )
    }

    @Test
    fun `a search matching only categorised books keeps the Default page`() {
        val loose = book(1, "loose")
        val novel = book(2, "novel", 1)

        val pages = pages(library = listOf(loose, novel), shown = listOf(novel))

        assertEquals(
            listOf("Default" to emptyList(), "Fiction" to listOf("novel"), "Poetry" to emptyList()),
            pages.summary()
        )
    }

    @Test
    fun `Default is left out only when every book has a category and the setting is off`() {
        val library = listOf(book(1, "novel", 1))

        assertEquals(listOf("Fiction", "Poetry"), pages(library).map { it.title })
        assertEquals(
            listOf("Default", "Fiction", "Poetry"),
            pages(library, alwaysShowDefault = true).map { it.title }
        )
    }

    @Test
    fun `with no other category there is a Default page, even before it is stored`() {
        assertEquals(
            listOf("Default" to listOf("novel")),
            pages(listOf(book(1, "novel")), categories = emptyList()).summary()
        )
    }

    @Test
    fun `the Default page keeps the stored category, so a sort change writes back its own`() {
        val stored = default.copy(sortOrder = LibrarySortOrder.PROGRESS)

        val page = pages(listOf(book(1, "loose")), categories = listOf(stored)).single()

        assertEquals(stored, page.category)
    }

    @Test
    fun `per-category sort uses each category's own order`() {
        val categories = listOf(
            default,
            fiction.copy(sortOrder = LibrarySortOrder.LAST_READ, sortOrderDescending = true)
        )
        val library = listOf(
            book(1, "a", 1, lastOpened = 1), book(2, "b", 1, lastOpened = 2)
        )

        assertEquals(
            listOf("b", "a"),
            pages(library, categories = categories, perCategorySort = true)[0]
                .books.map { it.data.title }
        )
        assertEquals(
            listOf("a", "b"),
            pages(library, categories = categories)[0].books.map { it.data.title }
        )
    }

    @Test
    fun `a page index past the end reads the last page`() {
        val pages = pages(listOf(book(1, "loose"), book(2, "novel", 1)))

        assertEquals("Poetry", pages.pageAt(7).title)
        assertEquals("Default", pages.pageAt(-1).title)
    }
}
