/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.settings.model

import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.acclorite.book_story.ui.theme.model.PureDark

class SettingTest {

    private val written = mutableListOf<String>()

    private fun setting() = Setting<PureDark, String>(
        key = stringPreferencesKey("pure_dark"),
        default = PureDark.OFF,
        setSetting = { written += it },
        serialize = { it.name },
        deserialize = { PureDark.valueOf(it) }
    )

    @Test
    fun `an unknown stored name shows the default and is not rewritten`() {
        val setting = setting()
        setting.init("OFX")
        assertEquals(PureDark.OFF, setting.lastValue)
        assertTrue(written.isEmpty())
    }

    @Test
    fun `a missing value shows the default and writes nothing`() {
        val setting = setting()
        setting.init(null)
        assertEquals(PureDark.OFF, setting.lastValue)
        assertTrue(written.isEmpty())
    }

    @Test
    fun `a known stored name is shown`() {
        val setting = setting()
        setting.init(PureDark.entries.last().name)
        assertEquals(PureDark.entries.last(), setting.lastValue)
    }

    @Test
    fun `an equal value is not written again`() {
        val setting = setting()
        val other = PureDark.entries.last()
        setting.update(other)
        setting.update(other)
        setting.update(other)
        assertEquals(listOf(other.name), written)
    }
}
