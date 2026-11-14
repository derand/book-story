/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library.model

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.core.helpers.compareByWithOrder
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.model.library.Category

/** The id of the Default category: the page of books in no other category. */
const val DEFAULT_CATEGORY_ID = -1

/**
 * One page of the Library pager, and one tab. [category] is the stored one, so
 * a sort change can be written back; [title] is what the tab shows.
 */
@Immutable
data class LibraryPage(
    val category: Category,
    val title: String,
    val books: List<SelectableBook>
)

/**
 * The Library's pages in tab order, each with its [shown] books sorted. Never
 * empty: the Default page is there whenever no other one is.
 *
 * Whether the Default page exists is decided from the whole [library], not
 * from [shown]: a search that matches only categorised books must not remove
 * it, or every page after it moves one place to the left.
 */
fun libraryPages(
    categories: List<Category>,
    library: List<Book>,
    shown: List<SelectableBook>,
    alwaysShowDefault: Boolean,
    perCategorySort: Boolean,
    sortOrder: LibrarySortOrder,
    sortOrderDescending: Boolean,
    defaultTitle: String
): List<LibraryPage> {
    val default = categories.find { it.id == DEFAULT_CATEGORY_ID }
        ?: Category(id = DEFAULT_CATEGORY_ID, title = "", order = -1)
    val others = categories.filterNot { it.id == DEFAULT_CATEGORY_ID }
    val otherIds = others.mapTo(HashSet()) { it.id }

    fun Book.isUncategorised() = this.categories.none { it in otherIds }

    val showDefault = alwaysShowDefault || others.isEmpty() ||
            library.any { it.isUncategorised() }

    return (others + listOfNotNull(default.takeIf { showDefault }))
        .sortedBy { it.order }
        .map { category ->
            val books = shown.filter { book ->
                if (category.id == DEFAULT_CATEGORY_ID) book.data.isUncategorised()
                else category.id in book.data.categories
            }
            LibraryPage(
                category = category,
                title = if (category.id == DEFAULT_CATEGORY_ID) defaultTitle else category.title,
                books = if (perCategorySort) {
                    books.sortedBy(category.sortOrder, category.sortOrderDescending)
                } else {
                    books.sortedBy(sortOrder, sortOrderDescending)
                }
            )
        }
}

/** The page at [index], or the nearest one while the pager has not caught up. */
fun List<LibraryPage>.pageAt(index: Int): LibraryPage = this[index.coerceIn(indices)]

private fun List<SelectableBook>.sortedBy(
    order: LibrarySortOrder,
    descending: Boolean
): List<SelectableBook> = sortedWith(
    compareByWithOrder(descending) { book ->
        when (order) {
            LibrarySortOrder.NAME -> book.data.title.trim()
            LibrarySortOrder.LAST_READ -> book.data.lastOpened
            LibrarySortOrder.PROGRESS -> book.data.progress
            LibrarySortOrder.AUTHOR -> book.data.author.getAsString()
        }
    }
)
