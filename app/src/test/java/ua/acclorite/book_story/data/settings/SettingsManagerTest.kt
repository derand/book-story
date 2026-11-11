/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.settings

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.local.data_store.DataStore
import ua.acclorite.book_story.ui.theme.model.PureDark
import java.io.IOException
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTest {

    private class FakeDataStore(
        private val stored: Map<String, Any> = emptyMap(),
        private val failReads: Boolean = false
    ) : DataStore {
        val writes: MutableList<Pair<String, Any?>> = Collections.synchronizedList(mutableListOf())

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> getNullableData(key: Preferences.Key<T>): T? {
            if (failReads) throw IOException("read failed")
            return stored[key.name] as T?
        }

        override suspend fun <T> putData(key: Preferences.Key<T>, value: T) {
            writes += key.name to value
        }
    }

    private fun SettingsManager.awaitInitialized() = runBlocking {
        withTimeout(5_000) { initialized.first { it } }
    }

    @Test
    fun `an unknown stored enum name starts with the default`() {
        val store = FakeDataStore(stored = mapOf("pure_dark" to "OFX"))
        val settings = SettingsManager(store)
        settings.awaitInitialized()

        assertEquals(PureDark.OFF, settings.pureDark.lastValue)
        assertTrue(store.writes.toString(), store.writes.isEmpty())
    }

    @Test
    fun `a failed read starts with defaults and writes nothing`() {
        val store = FakeDataStore(failReads = true)
        val settings = SettingsManager(store)
        settings.awaitInitialized()

        assertEquals(PureDark.OFF, settings.pureDark.lastValue)
        assertTrue(store.writes.toString(), store.writes.isEmpty())
    }
}
