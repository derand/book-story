/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which document identity a book keeps when its row is written.
 *
 * Every screen writes the whole row back from a copy it loaded earlier, and
 * those copies disagree about the identity, so only a caller that has just
 * asked the provider may change it. Every way of getting it wrong is silent:
 * erase the identity, or let an old copy overwrite a new one, and the book
 * resolves the slow way forever; keep a stale one past a move and it opens a
 * file the user has moved away from.
 */
class BookIdentityRuleTest {

    private val drive = "com.google.android.apps.docs.storage"
    private val path = "/storage/emulated/0/Books/solaris.fb2"

    private val identified = BookIdentity(path, drive, "acc=1;doc=A")
    private val unidentified = BookIdentity(path)

    @Test
    fun `a caller holding no identity does not erase the stored one`() {
        // The reader, leaving a book on the very open that learned its identity.
        val result = identityToWrite(incoming = unidentified, stored = identified)

        assertEquals(drive, result.documentAuthority)
        assertEquals("acc=1;doc=A", result.documentId)
    }

    @Test
    fun `a caller that states an identity has it written`() {
        // Adding a preview to the library, with the id of the file as the grant
        // reaches it rather than as the handing-over app did.
        val result = identityToWrite(
            incoming = BookIdentity(path, drive, "acc=1;doc=NEW"),
            stored = identified,
            statesIdentity = true
        )

        assertEquals("acc=1;doc=NEW", result.documentId)
    }

    @Test
    fun `a caller that states no identity has it erased`() {
        // A preview reached through a grant that answers with no document id:
        // the one the preview arrived with belongs to another provider.
        val result = identityToWrite(incoming = unidentified, stored = identified, statesIdentity = true)

        assertNull(result.documentAuthority)
        assertNull(result.documentId)
    }

    /**
     * The defect this fixes. The reader's copy is loaded before the open that
     * learns the identity and is written back right after it, so where the id
     * had changed — another device, after a restore — the old one won, and
     * every open looked it up in vain and then descended the path.
     */
    @Test
    fun `a caller holding an old identity does not overwrite the stored one`() {
        val result = identityToWrite(
            incoming = BookIdentity(path, drive, "acc=2;doc=OLD"),
            stored = identified
        )

        assertEquals("acc=1;doc=A", result.documentId)
    }

    /**
     * An earlier defect. Book info holds a copy loaded after the identity
     * was written, so it carries one — and a rule about the caller kept it,
     * leaving the row with a new path and an id describing the old place. The
     * book went on opening the file it had always opened.
     */
    @Test
    fun `a path that changed drops the identity, whatever the caller carries`() {
        val moved = identified.copy(filePath = "/storage/emulated/0/Download/solaris.fb2")

        listOf(false, true).forEach { states ->
            val result = identityToWrite(incoming = moved, stored = identified, statesIdentity = states)

            assertNull(result.documentAuthority)
            assertNull(result.documentId)
        }
    }

    @Test
    fun `a path that changed drops the identity when the caller carries none`() {
        // Keeping a previewed book by copying its file: the copy is the location
        // now, and the id belonged to the provider it came from.
        val stored = BookIdentity("/storage/emulated/0/Download/solaris.fb2", drive, "acc=1;doc=A")
        val result = identityToWrite(
            incoming = BookIdentity("/data/user/0/…/owned_books/7/solaris.fb2"),
            stored = stored
        )

        assertNull(result.documentAuthority)
        assertNull(result.documentId)
    }

    @Test
    fun `the path is what is written, in every case`() {
        val moved = identified.copy(filePath = "/elsewhere/solaris.fb2")

        assertEquals("/elsewhere/solaris.fb2", identityToWrite(moved, identified).filePath)
        assertEquals(path, identityToWrite(unidentified, identified).filePath)
    }

    @Test
    fun `a book with no row yet keeps what it came with`() {
        assertEquals(identified, identityToWrite(incoming = identified, stored = null))
        assertNull(identityToWrite(incoming = unidentified, stored = null).documentId)
    }

    @Test
    fun `neither side having an identity leaves none`() {
        assertNull(identityToWrite(incoming = unidentified, stored = unidentified).documentId)
    }

    /** An authority without an id is not an identity, and does not travel alone. */
    @Test
    fun `the authority follows the id it belongs to`() {
        val result = identityToWrite(incoming = unidentified, stored = identified)

        assertEquals(result.documentAuthority != null, result.documentId != null)
    }
}
