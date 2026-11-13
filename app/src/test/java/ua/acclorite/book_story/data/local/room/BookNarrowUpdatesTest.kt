/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.converter.CategoryConverter
import ua.acclorite.book_story.data.local.dto.BookEntity

/**
 * Every book update writes only the columns it is about. A screen holds a copy
 * of the row from whenever it loaded it; anything else written meanwhile must
 * survive its edit.
 */
@RunWith(RobolectricTestRunner::class)
class BookNarrowUpdatesTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val dao = database.bookDao

    @After
    fun tearDown() = database.close()

    private val stored = BookEntity(
        title = "A book",
        author = "An author",
        description = "About it",
        filePath = "/storage/a-book.fb2",
        scrollIndex = 12,
        scrollOffset = 34,
        progress = 0.5f,
        image = "file:///covers/1.webp",
        categories = listOf(1),
        inLibrary = true,
        previewUri = null,
        documentAuthority = "provider",
        documentId = "doc:1"
    )

    private suspend fun insert(entity: BookEntity = stored): Int =
        dao.insertBook(entity).toInt()

    private suspend fun row(id: Int): BookEntity = dao.findBookById(id)!!

    @Test
    fun `a position save leaves the rest of the row alone`() = runBlocking {
        val id = insert()

        assertEquals(1, dao.updatePosition(id, scrollIndex = 99, scrollOffset = 7, progress = 0.9f))

        assertEquals(
            stored.copy(id = id, scrollIndex = 99, scrollOffset = 7, progress = 0.9f),
            row(id)
        )
    }

    @Test
    fun `each detail is written on its own`() = runBlocking {
        val id = insert()

        dao.updateTitle(id, "New title")
        dao.updateAuthor(id, "New author")
        dao.updateDescription(id, null)
        dao.updateImage(id, null)

        assertEquals(
            stored.copy(
                id = id,
                title = "New title",
                author = "New author",
                description = null,
                image = null
            ),
            row(id)
        )
    }

    @Test
    fun `categories are written for every book named and no other`() = runBlocking {
        val first = insert()
        val second = insert()
        val untouched = insert()

        val changed = dao.updateCategories(
            ids = listOf(first, second),
            categories = CategoryConverter().fromCategoryList(listOf(2, 3))
        )

        assertEquals(2, changed)
        assertEquals(listOf(2, 3), row(first).categories)
        assertEquals(listOf(2, 3), row(second).categories)
        assertEquals(listOf(1), row(untouched).categories)
        assertEquals(stored.copy(id = first, categories = listOf(2, 3)), row(first))
    }

    @Test
    fun `a changed path drops the identity of the old place`() = runBlocking {
        val id = insert()

        dao.updateFilePath(id, "/storage/elsewhere.fb2")

        assertEquals(
            stored.copy(
                id = id,
                filePath = "/storage/elsewhere.fb2",
                documentAuthority = null,
                documentId = null
            ),
            row(id)
        )
    }

    @Test
    fun `the same path keeps its identity`() = runBlocking {
        val id = insert()

        dao.updateFilePath(id, stored.filePath)

        assertEquals(stored.copy(id = id), row(id))
    }

    @Test
    fun `an identity learned at an open leaves the rest alone`() = runBlocking {
        val id = insert()

        dao.updateIdentity(id, documentAuthority = "other", documentId = "doc:2")

        assertEquals(
            stored.copy(id = id, documentAuthority = "other", documentId = "doc:2"),
            row(id)
        )
    }

    @Test
    fun `a location write keeps the position and the details`() = runBlocking {
        val id = insert(stored.copy(inLibrary = false, previewUri = "content://x/1"))

        dao.updateLocation(
            id = id,
            filePath = "/data/books/1/a-book.fb2",
            inLibrary = true,
            previewUri = null,
            documentAuthority = null,
            documentId = null,
            originAuthority = "provider",
            originDocumentId = "doc:1",
            originPath = "/storage/a-book.fb2"
        )

        assertEquals(
            stored.copy(
                id = id,
                filePath = "/data/books/1/a-book.fb2",
                inLibrary = true,
                previewUri = null,
                documentAuthority = null,
                documentId = null,
                originAuthority = "provider",
                originDocumentId = "doc:1",
                originPath = "/storage/a-book.fb2"
            ),
            row(id)
        )
    }

    @Test
    fun `no such book changes nothing`() = runBlocking {
        assertEquals(0, dao.updatePosition(42, 1, 1, 0.1f))
        assertEquals(0, dao.updateTitle(42, "x"))
    }
}
