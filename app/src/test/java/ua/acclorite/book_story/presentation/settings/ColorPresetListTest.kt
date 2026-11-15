/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.ColorPreset

class ColorPresetListTest {

    private fun presets(vararg ids: Int, selected: Int? = null) = ids.map {
        ColorPreset.default.copy(id = it, name = "P$it", isSelected = it == selected)
    }

    @Test
    fun `drag steps applied in order give the order shown`() {
        val moved = presets(1, 2, 3).moved(0, 1).moved(1, 2)

        assertEquals(listOf(2, 3, 1), moved.map { it.id })
    }

    @Test
    fun `a step with an index out of range changes nothing`() {
        val list = presets(1, 2)

        assertSame(list, list.moved(0, 2))
        assertSame(list, list.moved(-1, 0))
    }

    @Test
    fun `the selected preset is the flagged one`() {
        assertEquals(2, presets(1, 2, 3, selected = 2).getSelectedColorPreset().id)
    }

    @Test
    fun `a single preset is selected even without the flag`() {
        assertEquals(7, presets(7).getSelectedColorPreset().id)
    }

    @Test
    fun `no presets or none flagged gives the default`() {
        assertEquals(ColorPreset.default, emptyList<ColorPreset>().getSelectedColorPreset())
        assertEquals(ColorPreset.default, presets(1, 2).getSelectedColorPreset())
    }
}
