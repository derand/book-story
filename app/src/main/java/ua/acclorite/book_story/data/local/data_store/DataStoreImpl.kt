/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.data_store

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore("data_store")

class DataStoreImpl @Inject constructor(context: Application) : DataStore {
    private val dataStore = context.dataStore

    // Null only for a value never stored; a failed read throws, so that the
    // caller does not take it for "never set".
    override suspend fun <T> getNullableData(key: Preferences.Key<T>): T? {
        return dataStore.data.first()[key]
    }

    override suspend fun <T> putData(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}