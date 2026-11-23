/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
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
import ua.acclorite.book_story.data.local.dto.ColorPresetEntity
import ua.acclorite.book_story.domain.model.reader.ColorPreset

/**
 * The built-in Light and Dark presets are seeded by Room's open callback, not
 * by the settings ViewModel's `init`.
 */
@RunWith(RobolectricTestRunner::class)
class BuiltInColorPresetsCallbackTest {

    private val app = RuntimeEnvironment.getApplication()
    private val name = "built_in_presets_db"

    private fun open(): BookDatabase = Room
        .databaseBuilder(app, BookDatabase::class.java, name)
        .addMigrations(*DatabaseHelper.MANUAL_MIGRATIONS)
        .addCallback(DatabaseHelper.BUILT_IN_COLOR_PRESETS_CALLBACK)
        .build()

    private fun <T> withDatabase(block: suspend (BookDatabase) -> T): T = runBlocking {
        val database = open()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun custom(order: Int, isSelected: Boolean) = ColorPresetEntity(
        name = "Mine",
        backgroundColor = 1L,
        fontColor = 2L,
        isSelected = isSelected,
        order = order
    )

    @After
    fun tearDown() {
        app.deleteDatabase(name)
    }

    @Test
    fun aNewDatabaseHasLightThenDarkWithDarkSelected() {
        val presets = withDatabase { it.colorPresetDao.getColorPresets() }

        assertEquals(listOf("LIGHT", "DARK"), presets.map { it.type })
        assertEquals(listOf(0, 1), presets.map { it.order })
        assertEquals(listOf(false, true), presets.map { it.isSelected })
        assertEquals(
            ColorPreset.builtInLight.backgroundColor.value.toLong(),
            presets[0].backgroundColor
        )
        assertEquals(ColorPreset.builtInDark.fontColor.value.toLong(), presets[1].fontColor)
    }

    @Test
    fun customPresetsKeepTheirSelectionAndBuiltInsGoAfterThem() {
        withDatabase {
            it.colorPresetDao.getColorPresets().forEach { preset ->
                it.colorPresetDao.deleteColorPreset(preset)
            }
            it.colorPresetDao.updateColorPreset(custom(order = 0, isSelected = true))
        }

        val presets = withDatabase { it.colorPresetDao.getColorPresets() }

        assertEquals(listOf("CUSTOM", "LIGHT", "DARK"), presets.map { it.type })
        assertEquals(listOf(0, 1, 2), presets.map { it.order })
        assertEquals(listOf(true, false, false), presets.map { it.isSelected })
    }

    @Test
    fun reopeningDoesNotDuplicateOrResetThem() {
        withDatabase {
            val dark = it.colorPresetDao.getColorPresets().first { p -> p.type == "DARK" }
            it.colorPresetDao.updateColorPreset(dark.copy(backgroundColor = 42L))
            it.colorPresetDao.selectOnly(
                it.colorPresetDao.getColorPresets().first { p -> p.type == "LIGHT" }.id!!
            )
        }

        val presets = withDatabase { it.colorPresetDao.getColorPresets() }

        assertEquals(listOf("LIGHT", "DARK"), presets.map { it.type })
        assertEquals(42L, presets[1].backgroundColor)
        assertEquals(listOf(true, false), presets.map { it.isSelected })
    }

    @Test
    fun aMissingBuiltInIsBackOnTheNextOpenWithoutChangingTheSelection() {
        withDatabase {
            val presets = it.colorPresetDao.getColorPresets()
            it.colorPresetDao.updateColorPreset(custom(order = 2, isSelected = false))
            it.colorPresetDao.selectOnly(
                it.colorPresetDao.getColorPresets().first { p -> p.type == "CUSTOM" }.id!!
            )
            it.colorPresetDao.deleteColorPreset(presets.first { p -> p.type == "LIGHT" })
        }

        val presets = withDatabase { it.colorPresetDao.getColorPresets() }

        assertEquals(listOf("DARK", "CUSTOM", "LIGHT"), presets.map { it.type })
        assertEquals(listOf(false, true, false), presets.map { it.isSelected })
    }
}
