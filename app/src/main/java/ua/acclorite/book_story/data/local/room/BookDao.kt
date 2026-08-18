/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ua.acclorite.book_story.data.local.dto.BookEntity

@Dao
interface BookDao {
    /** Returns the new row id, which is how a freshly parsed preview is opened. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(
        book: BookEntity
    ): Long

    /**
     * Every list of books in the app comes from here — the library, the history
     * and the search — so this is the one place a preview has to be kept out of.
     *
     * Unfiltered on purpose: a search query is matched in Kotlin, by
     * [ua.acclorite.book_story.domain.model.library.matchesSearch], because
     * SQLite's `LOWER()` folds ASCII only and `LIKE` would read a typed `%` or
     * `_` as a wildcard. This is the same handful of rows either way.
     */
    @Query("SELECT * FROM bookentity WHERE inLibrary = 1")
    suspend fun getLibraryBooks(): List<BookEntity>

    /**
     * Deliberately **not** filtered by [BookEntity.inLibrary]: this is how the
     * reader loads the book it was asked for, and a preview is exactly the book
     * that needs loading.
     */
    @Query("SELECT * FROM bookentity WHERE id=:id")
    suspend fun findBookById(id: Int): BookEntity?

    /** Previews left behind, which by design means "left behind by a crash". */
    @Query("SELECT * FROM bookentity WHERE inLibrary = 0")
    suspend fun findPreviews(): List<BookEntity>

    @Delete
    suspend fun deleteBook(book: BookEntity): Int

    @Update
    suspend fun updateBook(book: BookEntity): Int
}