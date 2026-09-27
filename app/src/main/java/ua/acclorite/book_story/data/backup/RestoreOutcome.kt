/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import android.app.Application
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ua.acclorite.book_story.core.log.logW
import ua.acclorite.book_story.core.log.messageForLog
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RestoreOutcome"

/**
 * What a swap at startup did, kept until the user has been told.
 *
 * [failed] means the swap was given up: the library is whatever was in place
 * by then, which may be partly the backup. [sources] are the folders the backup
 * came from — none of them carry over a reinstall, and a Drive book is found
 * only through the very folder it was added from.
 */
data class RestoreOutcome(
    val failed: Boolean,
    val books: Int,
    val sessions: Int,
    val sources: List<BackupManifest.Source>
) {

    fun toJson(): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("failed", failed)
            put("books", books)
            put("sessions", sessions)
            put("sources", buildJsonArray { sources.forEach { add(it.toJson()) } })
        }
    )

    companion object {
        fun fromJson(text: String): RestoreOutcome {
            val root = Json.parseToJsonElement(text).jsonObject
            return RestoreOutcome(
                failed = root["failed"]?.jsonPrimitive?.boolean ?: false,
                books = root["books"]?.jsonPrimitive?.int ?: 0,
                sessions = root["sessions"]?.jsonPrimitive?.int ?: 0,
                sources = root["sources"]?.jsonArray.orEmpty()
                    .map { BackupManifest.Source.fromJson(it.jsonObject) }
            )
        }
    }
}

/** The [RestoreFiles.DONE_NOTE] the swap leaves for the Library. */
@Singleton
class RestoreOutcomeNote @Inject constructor(
    private val application: Application
) {

    private val file: File get() = File(application.filesDir, RestoreFiles.DONE_NOTE)

    /** The outcome of the last swap, or null when there is nothing to tell. */
    fun read(): RestoreOutcome? {
        if (!file.isFile) return null
        return try {
            RestoreOutcome.fromJson(file.readText())
        } catch (e: Exception) {
            // Told as a failure rather than dropped: a swap did run, and saying
            // nothing is how a half-replaced library goes unnoticed.
            logW(TAG, "The note does not read: ${e.messageForLog()}")
            RestoreOutcome(failed = true, books = 0, sessions = 0, sources = emptyList())
        }
    }

    fun clear() {
        file.delete()
    }
}
