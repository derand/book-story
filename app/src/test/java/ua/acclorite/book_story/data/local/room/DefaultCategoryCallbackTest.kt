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

/**
 * The default category (id -1) is created by Room's open callback, not by a
 * call on whatever thread first injects the database.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultCategoryCallbackTest {

    private val app = RuntimeEnvironment.getApplication()
    private val name = "default_category_db"

    private fun open(): BookDatabase = Room
        .databaseBuilder(app, BookDatabase::class.java, name)
        .addMigrations(*DatabaseHelper.MANUAL_MIGRATIONS)
        .addCallback(DatabaseHelper.DEFAULT_CATEGORY_CALLBACK)
        .build()

    @After
    fun tearDown() {
        app.deleteDatabase(name)
    }

    @Test
    fun aNewDatabaseHasTheDefaultCategory() = runBlocking {
        val database = open()
        val categories = database.categoryDao.getCategories()
        database.close()

        assertEquals(listOf(-1), categories.map { it.id })
        assertEquals("", categories.single().title)
    }

    @Test
    fun aMissingDefaultCategoryIsBackOnTheNextOpen() = runBlocking {
        val first = open()
        first.categoryDao.deleteCategory(first.categoryDao.getCategories().single())
        assertEquals(0, first.categoryDao.getCategoriesCount())
        first.close()

        val second = open()
        val ids = second.categoryDao.getCategories().map { it.id }
        second.close()

        assertEquals(listOf(-1), ids)
    }

    @Test
    fun reopeningDoesNotDuplicateOrResetIt() = runBlocking {
        val first = open()
        val default = first.categoryDao.getCategories().single()
        first.categoryDao.updateCategory(default.copy(sortOrderDescending = false))
        first.close()

        val second = open()
        val categories = second.categoryDao.getCategories()
        second.close()

        assertEquals(1, categories.size)
        assertEquals(false, categories.single().sortOrderDescending)
    }
}
