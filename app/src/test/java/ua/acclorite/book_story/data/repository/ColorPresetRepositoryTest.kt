/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import androidx.compose.ui.graphics.Color
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.mapper.color_preset.ColorPresetMapperImpl
import ua.acclorite.book_story.domain.model.reader.ColorPreset

/** Reorder and select write only what they change, in one statement or transaction. */
@RunWith(RobolectricTestRunner::class)
class ColorPresetRepositoryTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val repository = ColorPresetRepositoryImpl(database, ColorPresetMapperImpl())

    @After
    fun tearDown() = database.close()

    private suspend fun addPresets(vararg names: String): List<ColorPreset> {
        names.forEach { name ->
            repository.updateColorPreset(
                ColorPreset.default.copy(name = name, backgroundColor = Color.Red)
            ).getOrThrow()
        }
        return repository.getColorPresets().getOrThrow()
    }

    @Test
    fun `reorder stores the new order and keeps every preset`() = runBlocking {
        val (a, b, c) = addPresets("A", "B", "C")

        repository.reorderColorPresets(listOf(b, c, a)).getOrThrow()

        val stored = repository.getColorPresets().getOrThrow()
        assertEquals(listOf("B", "C", "A"), stored.map { it.name })
        assertEquals(listOf(b.id, c.id, a.id), stored.map { it.id })
    }

    @Test
    fun `reorder writes the order only, not stale colours`() = runBlocking {
        val (a, b) = addPresets("A", "B")
        repository.updateColorPreset(b.copy(backgroundColor = Color.Blue)).getOrThrow()

        repository.reorderColorPresets(listOf(b, a)).getOrThrow()

        val stored = repository.getColorPresets().getOrThrow()
        assertEquals(Color.Blue, stored.first { it.id == b.id }.backgroundColor)
    }

    @Test
    fun `select leaves exactly one preset selected`() = runBlocking {
        val (_, b, c) = addPresets("A", "B", "C")

        repository.selectColorPreset(b).getOrThrow()
        repository.selectColorPreset(c).getOrThrow()

        val selected = repository.getColorPresets().getOrThrow().filter { it.isSelected }
        assertEquals(listOf(c.id), selected.map { it.id })
    }
}
