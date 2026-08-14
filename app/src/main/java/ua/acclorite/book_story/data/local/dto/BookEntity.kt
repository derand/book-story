/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ua.acclorite.book_story.data.converter.CategoryConverter

@Entity
@TypeConverters(CategoryConverter::class)
data class BookEntity(
    @PrimaryKey(true) val id: Int = 0,
    val title: String,
    val author: String,
    val description: String?,
    val filePath: String,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val progress: Float,
    val image: String? = null,
    @ColumnInfo(defaultValue = "[]") val categories: List<Int>,

    /**
     * Whether the book belongs to the library, or is only being previewed.
     *
     * A book opened from a file manager is parsed into a real row so the reader,
     * which is keyed on a row id throughout, works unchanged — but it is not the
     * user's book yet. It stays out of every list until they say so, and is
     * deleted if they leave without saying so.
     *
     * Defaults to 1: every book that existed before this column was added got
     * into the database the only way there was, by being imported.
     */
    @ColumnInfo(defaultValue = "1") val inLibrary: Boolean = true,

    /**
     * The URI another app handed the file over on, for a preview only.
     *
     * A library book is found by descending a persisted grant to its path; a
     * preview has no such grant, so the URI is the only way back to its bytes.
     * It is cleared the moment the book is added, because from then on the book
     * must be reachable the way every other one is — the grant behind this URI
     * dies with the task.
     */
    val previewUri: String? = null
)