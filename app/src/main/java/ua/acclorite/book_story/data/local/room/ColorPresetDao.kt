/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ua.acclorite.book_story.data.local.dto.ColorPresetEntity

@Dao
interface ColorPresetDao {
    @Upsert
    suspend fun updateColorPreset(colorPreset: ColorPresetEntity)

    @Query("SELECT `order` FROM colorpresetentity WHERE id = :id")
    suspend fun getColorPresetOrder(id: Int): Int

    @Query("SELECT COUNT(*) FROM colorpresetentity")
    suspend fun getColorPresetsSize(): Int

    @Query("SELECT * FROM colorpresetentity ORDER BY `order` ASC")
    suspend fun getColorPresets(): List<ColorPresetEntity>

    @Delete
    suspend fun deleteColorPreset(colorPreset: ColorPresetEntity)

    /** One statement, so exactly one preset is selected even if interrupted. */
    @Query("UPDATE colorpresetentity SET isSelected = (id = :id)")
    suspend fun selectOnly(id: Int)

    @Query("UPDATE colorpresetentity SET `order` = :order WHERE id = :id")
    suspend fun updateColorPresetOrder(id: Int, order: Int)

    /**
     * Writes only the order, all or nothing. It used to delete every row and
     * insert them back one by one, so a kill in between left no presets.
     */
    @Transaction
    suspend fun reorderColorPresets(ids: List<Int>) {
        ids.forEachIndexed { order, id -> updateColorPresetOrder(id, order) }
    }
}