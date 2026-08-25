/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which granted trees Browse lists. The provider answers whether one holds
 * another; this is everything decided around that answer, and it is where the
 * mistakes live — the previous rule compared invented paths and made the device
 * library disappear.
 */
class RootsToListTest {

    private val device = SafRoot("com.android.externalstorage.documents", "primary:Download")
    private val deviceBooks =
        SafRoot("com.android.externalstorage.documents", "primary:Download/books_story")
    private val driveBooks = SafRoot("com.google.android.apps.docs.storage", "acc=1;doc=books")
    private val driveFiction =
        SafRoot("com.google.android.apps.docs.storage", "acc=1;doc=fiction")

    /** Nobody holds anybody: the provider is sure, and says no. */
    private val nothingNested: (SafRoot, SafRoot) -> Boolean? = { _, _ -> false }

    /** The provider will not answer at all. */
    private val silent: (SafRoot, SafRoot) -> Boolean? = { _, _ -> null }

    private fun answering(vararg pairs: Pair<SafRoot, SafRoot>): (SafRoot, SafRoot) -> Boolean? =
        { parent, child -> pairs.any { it.first == parent && it.second == child } }

    @Test
    fun `unrelated roots are all listed`() {
        assertEquals(
            listOf(device, driveBooks),
            rootsToList(listOf(device, driveBooks), nothingNested)
        )
    }

    @Test
    fun `a root inside another is dropped`() {
        assertEquals(
            listOf(driveBooks),
            rootsToList(
                listOf(driveBooks, driveFiction),
                answering(driveBooks to driveFiction)
            )
        )
    }

    @Test
    fun `the parent survives whichever order the roots arrive in`() {
        assertEquals(
            listOf(driveBooks),
            rootsToList(
                listOf(driveFiction, driveBooks),
                answering(driveBooks to driveFiction)
            )
        )
    }

    /**
     * The defect this replaces: Drive reports "/storage" for both of its folders
     * and the real library lives at "/storage/emulated/0/...", so a prefix test
     * dropped the library. Identity by document id cannot express that mistake —
     * and the predicate is never even asked across authorities, since
     * `isChildDocument` throws there.
     */
    @Test
    fun `a root of another authority is never dropped, and never asked about`() {
        var askedAcrossAuthorities = false
        val result = rootsToList(listOf(driveBooks, deviceBooks)) { parent, child ->
            if (parent.authority != child.authority) askedAcrossAuthorities = true
            true
        }

        assertEquals(listOf(driveBooks, deviceBooks), result)
        assertFalse(askedAcrossAuthorities)
    }

    @Test
    fun `the same tree granted twice is listed once`() {
        assertEquals(
            listOf(driveBooks),
            rootsToList(listOf(driveBooks, driveBooks), nothingNested)
        )
    }

    @Test
    fun `a chain collapses to its outermost root`() {
        val deeper =
            SafRoot("com.android.externalstorage.documents", "primary:Download/books_story/tmp")
        assertEquals(
            listOf(device),
            rootsToList(
                listOf(device, deviceBooks, deeper),
                answering(
                    device to deviceBooks,
                    device to deeper,
                    deviceBooks to deeper
                )
            )
        )
    }

    /**
     * Fail open. A provider that will not answer must not be able to empty the
     * list: a book listed twice is a nuisance, a library that vanished is the bug.
     */
    @Test
    fun `an unanswerable pair keeps both roots`() {
        assertEquals(
            listOf(driveBooks, driveFiction),
            rootsToList(listOf(driveBooks, driveFiction), silent)
        )
    }

    /** Two roots that each claim to hold the other cannot both be dropped. */
    @Test
    fun `mutual containment keeps both roots`() {
        assertEquals(
            listOf(driveBooks, driveFiction),
            rootsToList(
                listOf(driveBooks, driveFiction),
                answering(driveBooks to driveFiction, driveFiction to driveBooks)
            )
        )
    }

    /** Silence falls back to the ids, which for this provider are paths. */
    @Test
    fun `a silent provider still nests hierarchical ids`() {
        assertEquals(
            listOf(device),
            rootsToList(listOf(device, deviceBooks), silent)
        )
    }

    @Test
    fun `a silent provider does not nest opaque ids`() {
        assertEquals(
            listOf(driveBooks, driveFiction),
            rootsToList(listOf(driveBooks, driveFiction), silent)
        )
    }

    @Test
    fun `each ordered pair is asked at most once`() {
        val asked = mutableListOf<Pair<SafRoot, SafRoot>>()
        rootsToList(listOf(driveBooks, driveFiction)) { parent, child ->
            asked.add(parent to child)
            false
        }

        assertEquals(asked.size, asked.distinct().size)
    }

    @Test
    fun `one root needs no question at all`() {
        var asked = false
        val result = rootsToList(listOf(driveBooks)) { _, _ ->
            asked = true
            true
        }

        assertEquals(listOf(driveBooks), result)
        assertFalse(asked)
    }

    @Test
    fun `no roots is no list`() {
        assertEquals(emptyList<SafRoot>(), rootsToList(emptyList(), nothingNested))
    }

    @Test
    fun `document ids nest only on a segment boundary`() {
        assertTrue(documentIdContains("primary:Download", "primary:Download/books_story"))
        assertFalse(documentIdContains("primary:Download", "primary:DownloadOld"))
        assertFalse(documentIdContains("primary:Download", "primary:Download"))
        assertFalse(documentIdContains("primary:Download/books_story", "primary:Download"))
    }

    /** A whole volume holds everything on it, and its children follow the colon. */
    @Test
    fun `a volume root holds its top-level entries`() {
        assertTrue(documentIdContains("primary:", "primary:Download"))
    }

    @Test
    fun `an empty parent id holds nothing`() {
        assertFalse(documentIdContains("", "primary:Download"))
    }

    /** An id is opaque; only its issuer knows whether two spellings are one thing. */
    @Test
    fun `document ids are compared exactly`() {
        assertFalse(documentIdContains("primary:download", "primary:Download/books"))
    }
}
