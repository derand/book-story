/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * What may reach logcat out of a message the app did not write.
 *
 * The call sites were walked for #109 and no longer name a book, but an
 * exception's own message is not ours to word: the platform puts the path of
 * the file it failed on into it. These are the messages the device actually
 * produces, so the test is the evidence that the walk was not undone by them.
 */
class WithoutLocationsTest {

    @Test
    fun `a path keeps nothing of itself, and the reason after it survives`() {
        assertEquals(
            "…: open failed: ENOENT (No such file or directory)",
            withoutLocations(
                "/storage/emulated/0/Download/solaris.fb2: open failed: ENOENT " +
                    "(No such file or directory)"
            )
        )
    }

    /** The name is the book, and a name with spaces must not survive the path. */
    @Test
    fun `a file name holding spaces goes with its path`() {
        assertEquals(
            "…: open failed: EACCES (Permission denied)",
            withoutLocations(
                "/storage/emulated/0/Books/Vol. 1 — Sci-Fi & Co.epub: open failed: " +
                    "EACCES (Permission denied)"
            )
        )
    }

    @Test
    fun `a path in the middle of a message goes too`() {
        assertEquals(
            "Failed to copy … (No such file)",
            withoutLocations("Failed to copy /data/user/0/ua.acclorite.book_story/files/x.epub (No such file)")
        )
    }

    /** The authority names the provider; the document id names the folder. */
    @Test
    fun `a document URI is kept as far as its authority`() {
        assertEquals(
            "content://com.android.externalstorage.documents/…",
            withoutLocations(
                "content://com.android.externalstorage.documents/tree/" +
                    "primary%3ABooks/document/primary%3ABooks%2Fsolaris.fb2"
            )
        )
    }

    @Test
    fun `a file URI keeps its scheme and nothing else`() {
        assertEquals(
            "file:///…",
            withoutLocations("file:///storage/emulated/0/Books/solaris.fb2")
        )
    }

    @Test
    fun `an authority on its own gains no path it did not have`() {
        assertEquals(
            "content://com.android.providers.downloads.documents",
            withoutLocations("content://com.android.providers.downloads.documents")
        )
    }

    @Test
    fun `several locations in one message all go`() {
        assertEquals(
            "… could not be moved to …",
            withoutLocations("/data/user/0/pkg/cache/a.epub could not be moved to /data/user/0/pkg/files/a.epub")
        )
    }

    @Test
    fun `a message naming no location is left as it is`() {
        listOf(
            "invalid CEN header (bad signature)",
            "Unexpected end of ZLIB input stream",
            "no such column: title_x",
            "read and/or write failed"
        ).forEach { message ->
            assertEquals(message, withoutLocations(message))
        }
    }

    /** A single segment is a word with a slash in front of it, not a path. */
    @Test
    fun `a lone slashed word is not taken for a path`() {
        assertEquals("write failed on /dev", withoutLocations("write failed on /dev"))
    }

    @Test
    fun `an exception carries its own name in front of what is left`() {
        assertEquals(
            "FileNotFoundException: …: open failed: ENOENT",
            FileNotFoundException("/storage/emulated/0/Books/solaris.fb2: open failed: ENOENT")
                .messageForLog()
        )
    }

    /** In place of the `null` these lines printed before. */
    @Test
    fun `an exception with no message is logged by its name`() {
        assertEquals("IOException", IOException().messageForLog())
    }
}
