/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The version of the archive's own layout — which entries exist and what they
 * hold. Raised only when a reader of the old layout would misread the new one;
 * a new manifest field that an old reader can ignore does not count.
 */
const val BACKUP_FORMAT = 1

/** Where each part of a backup lives inside the zip. */
object BackupEntries {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database/book_db"
    const val SETTINGS = "datastore/data_store.preferences_pb"
    const val COVERS = "covers/"
    const val OWNED_BOOKS = "owned_books/"
}

/**
 * What a backup says about itself, written first so a restore can decide
 * whether to go on before reading anything else.
 *
 * [schema] is the one that decides: an older database is migrated by Room on
 * the next start, a newer one cannot be opened by this app at all and has to be
 * refused before anything is replaced. [format] guards the archive's layout the
 * same way. [versionCode] and [applicationId] are for a person reading the
 * file: a backup restores into any variant of the app.
 *
 * [filesDir] is the private directory the backup was taken from. The database
 * holds absolute paths into it — a cover's `file://` URI, the path of a book
 * the app keeps a copy of — and they contain the Android user id, so a restore
 * into a work profile rebases them from this prefix onto its own.
 *
 * [sources] are the folders the user had granted. A grant cannot be carried
 * over, and for a provider such as Google Drive a book's path is counted from
 * the granted folder itself, so only granting *the same* folders again finds
 * the books again. Naming them is what makes the hint after a restore useful.
 */
data class BackupManifest(
    val format: Int,
    val schema: Int,
    val versionCode: Int,
    val versionName: String,
    val applicationId: String,
    val filesDir: String,
    val createdAt: Long,
    val counts: Counts,
    val sources: List<Source>
) {

    /** What the confirmation before a restore lists. */
    data class Counts(
        val books: Int,
        val sessions: Int,
        val covers: Int,
        val ownedBooks: Int
    )

    /** A granted folder, by what a person would recognise it by. */
    data class Source(
        /** The app holding it — "Drive", "External Storage" — if it was known. */
        val provider: String?,
        /** What the provider calls the folder. */
        val name: String?,
        val authority: String?
    )

    fun toJson(): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", format)
            put("schema", schema)
            put("versionCode", versionCode)
            put("versionName", versionName)
            put("applicationId", applicationId)
            put("filesDir", filesDir)
            put("createdAt", createdAt)
            put("counts", buildJsonObject {
                put("books", counts.books)
                put("sessions", counts.sessions)
                put("covers", counts.covers)
                put("ownedBooks", counts.ownedBooks)
            })
            put("sources", buildJsonArray {
                sources.forEach { source ->
                    add(buildJsonObject {
                        put("provider", source.provider)
                        put("name", source.name)
                        put("authority", source.authority)
                    })
                }
            })
        }
    )

    companion object {
        private val json = Json { prettyPrint = true }

        /**
         * Reads a manifest, or throws. Every field is required: a manifest
         * missing one is not a backup this app wrote, and guessing a default
         * for [schema] is exactly how a newer database would get past the check.
         */
        fun fromJson(text: String): BackupManifest {
            val root = Json.parseToJsonElement(text).jsonObject
            fun JsonObject.string(key: String): String? =
                get(key)?.jsonPrimitive?.takeIf { it.isString }?.content
            fun JsonObject.requireString(key: String): String =
                string(key) ?: throw IllegalArgumentException("No \"$key\" in the manifest.")
            fun JsonObject.requireInt(key: String): Int =
                get(key)?.jsonPrimitive?.int
                    ?: throw IllegalArgumentException("No \"$key\" in the manifest.")

            val counts = root["counts"]?.jsonObject
                ?: throw IllegalArgumentException("No \"counts\" in the manifest.")

            return BackupManifest(
                format = root.requireInt("format"),
                schema = root.requireInt("schema"),
                versionCode = root.requireInt("versionCode"),
                versionName = root.requireString("versionName"),
                applicationId = root.requireString("applicationId"),
                filesDir = root.requireString("filesDir"),
                createdAt = root["createdAt"]?.jsonPrimitive?.long
                    ?: throw IllegalArgumentException("No \"createdAt\" in the manifest."),
                counts = Counts(
                    books = counts.requireInt("books"),
                    sessions = counts.requireInt("sessions"),
                    covers = counts.requireInt("covers"),
                    ownedBooks = counts.requireInt("ownedBooks")
                ),
                sources = root["sources"]?.jsonArray.orEmpty().map { element ->
                    val source = element.jsonObject
                    Source(
                        provider = source.string("provider"),
                        name = source.string("name"),
                        authority = source.string("authority")
                    )
                }
            )
        }
    }
}
