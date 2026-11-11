/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.settings.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class Setting<T, P>(
    val key: Preferences.Key<P>,
    private val default: T,
    private val setSetting: (P) -> Unit,
    private val serialize: (T) -> P,
    private val deserialize: (P) -> T,
    private val onUnreadable: (P, Exception) -> Unit = { _, _ -> }
) {
    private val _value = MutableStateFlow<T>(default)
    val value: T
        @Composable get() = _value.collectAsStateWithLifecycle().value
    val lastValue: T
        get() = _value.value

    /**
     * Shows [value] and stores it. A value equal to the one shown is neither
     * shown again nor stored: a slider drag sends the same step many times.
     */
    fun update(value: T) {
        if (_value.value == value) return
        _value.value = value
        setSetting(serialize(value))
    }

    /**
     * Shows the [stored] value, or the default when there is none or it cannot
     * be read back — a name an older build does not know, after a restore or a
     * downgrade. Nothing is written here: a missing value stays missing, so a
     * default changed in code reaches everyone who never chose, and an
     * unreadable one stays for a build that can read it.
     */
    fun init(stored: P?) {
        if (stored == null) return
        _value.value = try {
            deserialize(stored)
        } catch (e: Exception) {
            onUnreadable(stored, e)
            default
        }
    }
}
