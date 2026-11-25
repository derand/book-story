/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class CategoryConverter {

    @TypeConverter
    fun fromCategoryList(value: List<Int>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toCategoryList(value: String): List<Int> {
        return Json.decodeFromString(value)
    }
}