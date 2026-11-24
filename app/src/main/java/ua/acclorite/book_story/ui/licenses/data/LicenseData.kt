/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.licenses.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.R

/**
 * The bundled licence list (`res/raw/aboutlibraries.json`, ~128 KB), parsed once per
 * process, off the main thread, and shared by Licenses and License info.
 */
object LicenseData {

    private val mutex = Mutex()

    @Volatile
    private var libraries: List<Library>? = null

    /** The cached list, or null if it has not been parsed yet. */
    val cached: List<Library>? get() = libraries

    /** All libraries, open-source ones last (the order the Licenses screen shows). */
    suspend fun load(context: Context): List<Library> = load {
        context.applicationContext.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader().use { it.readText() }
    }

    /** [load] with the JSON read by [readJson], on [Dispatchers.Default]. */
    internal suspend fun load(readJson: () -> String): List<Library> {
        libraries?.let { return it }
        return mutex.withLock {
            libraries ?: withContext(Dispatchers.Default) {
                Libs.Builder().withJson(readJson()).build()
                    .libraries.sortedBy { it.openSource }
            }.also { libraries = it }
        }
    }
}

/** The licence list, null until it is parsed. */
@Composable
fun rememberLicenses(): State<List<Library>?> {
    val context = LocalContext.current
    return produceState(initialValue = LicenseData.cached) {
        value = LicenseData.load(context)
    }
}
