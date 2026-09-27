/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The manifest is what a restore trusts before it replaces anything, so what
 * matters is that it reads back exactly as written and that a manifest missing
 * the field the refusal depends on is refused rather than defaulted.
 */
class BackupManifestTest {

    private val manifest = BackupManifest(
        format = BACKUP_FORMAT,
        schema = 24,
        versionCode = 42,
        versionName = "1.8.0",
        applicationId = "ua.acclorite.book_story.release.debug",
        filesDir = "/data/user/0/ua.acclorite.book_story.release.debug/files",
        createdAt = 1_790_000_000_000,
        counts = BackupManifest.Counts(books = 53, sessions = 110, covers = 40, ownedBooks = 2),
        sources = listOf(
            BackupManifest.Source(
                provider = "External Storage",
                name = "books_story",
                authority = "com.android.externalstorage.documents"
            ),
            BackupManifest.Source(
                provider = "Drive",
                name = "Touhou",
                authority = "com.google.android.apps.docs.storage"
            ),
            // A provider that did not answer: nothing but the authority known.
            BackupManifest.Source(provider = null, name = null, authority = "gone.provider")
        )
    )

    @Test
    fun `reads back what was written`() {
        assertEquals(manifest, BackupManifest.fromJson(manifest.toJson()))
    }

    @Test
    fun `a manifest without the schema is refused, not defaulted`() {
        val json = manifest.toJson().replace(Regex("\"schema\"\\s*:\\s*24,?"), "")

        assertThrows(IllegalArgumentException::class.java) {
            BackupManifest.fromJson(json)
        }
    }

    @Test
    fun `a manifest without sources reads as none`() {
        val json = manifest.toJson().replace(Regex(",\\s*\"sources\"\\s*:\\s*\\[[^\\]]*\\]"), "")

        assertEquals(emptyList<BackupManifest.Source>(), BackupManifest.fromJson(json).sources)
    }

    @Test
    fun `text that is not a manifest is refused`() {
        assertThrows(Exception::class.java) {
            BackupManifest.fromJson("PK\u0003\u0004 not json")
        }
    }
}
