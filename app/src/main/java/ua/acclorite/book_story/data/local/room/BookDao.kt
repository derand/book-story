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

    /*
     * Each update below writes only the columns it is about. A screen holds a
     * copy of the row from whenever it last loaded it, and writing that copy
     * back whole reverted whatever had changed since — the identity learned at
     * an open, a book kept from a preview, a category set elsewhere. Each
     * returns the number of rows changed: 0 means there was no such book.
     */

    /** Where the reader is. The hot path: every settled scroll lands here. */
    @Query(
        "UPDATE bookentity SET scrollIndex = :scrollIndex, scrollOffset = :scrollOffset, " +
                "progress = :progress WHERE id = :id"
    )
    suspend fun updatePosition(id: Int, scrollIndex: Int, scrollOffset: Int, progress: Float): Int

    @Query("UPDATE bookentity SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Int, title: String): Int

    @Query("UPDATE bookentity SET author = :author WHERE id = :id")
    suspend fun updateAuthor(id: Int, author: String): Int

    @Query("UPDATE bookentity SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Int, description: String?): Int

    @Query("UPDATE bookentity SET image = :image WHERE id = :id")
    suspend fun updateImage(id: Int, image: String?): Int

    /** [categories] is the column's JSON, as `CategoryConverter` writes it. */
    @Query("UPDATE bookentity SET categories = :categories WHERE id IN (:ids)")
    suspend fun updateCategories(ids: List<Int>, categories: String): Int

    /**
     * The path set by hand. A path that changed is somewhere else, so the
     * identity of the old place goes with it; the same path keeps it. SQLite
     * reads every right-hand side from the row as it was, so the comparison
     * sees the old path.
     */
    @Query(
        "UPDATE bookentity SET " +
                "documentAuthority = CASE WHEN filePath = :filePath THEN documentAuthority ELSE NULL END, " +
                "documentId = CASE WHEN filePath = :filePath THEN documentId ELSE NULL END, " +
                "filePath = :filePath WHERE id = :id"
    )
    suspend fun updateFilePath(id: Int, filePath: String): Int

    /** What the provider just answered about the file, at an open. */
    @Query(
        "UPDATE bookentity SET documentAuthority = :documentAuthority, " +
                "documentId = :documentId WHERE id = :id"
    )
    suspend fun updateIdentity(id: Int, documentAuthority: String?, documentId: String?): Int

    /**
     * Where the book's bytes are read from, as a whole: storing, refreshing or
     * releasing an own copy, and keeping a preview. These columns change
     * together and only together, and every caller has just decided all of them.
     */
    @Query(
        "UPDATE bookentity SET filePath = :filePath, inLibrary = :inLibrary, " +
                "previewUri = :previewUri, documentAuthority = :documentAuthority, " +
                "documentId = :documentId, originAuthority = :originAuthority, " +
                "originDocumentId = :originDocumentId, originPath = :originPath WHERE id = :id"
    )
    suspend fun updateLocation(
        id: Int,
        filePath: String,
        inLibrary: Boolean,
        previewUri: String?,
        documentAuthority: String?,
        documentId: String?,
        originAuthority: String?,
        originDocumentId: String?,
        originPath: String?
    ): Int
}