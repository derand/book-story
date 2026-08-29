/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule that decides which sources are offered a local copy. */
class SourceLocalityTest {

    private val externalStorage = "com.android.externalstorage.documents"
    private val drive = "com.google.android.apps.docs.storage"
    private val downloads = "com.android.providers.downloads.documents"

    @Test
    fun `external storage is the device`() {
        assertTrue(SourceLocality.isDeviceStorage(externalStorage))
    }

    @Test
    fun `every other provider is not`() {
        assertFalse(SourceLocality.isDeviceStorage(drive))
        assertFalse(SourceLocality.isDeviceStorage(downloads))
        assertFalse(SourceLocality.isDeviceStorage("com.example.books"))
    }

    @Test
    fun `an unnamed source is not the device`() {
        assertFalse(SourceLocality.isDeviceStorage(null))
        assertFalse(SourceLocality.isDeviceStorage(""))
    }

    @Test
    fun `a copy is offered for everything but the device`() {
        assertFalse(SourceLocality.offersLocalCopy(externalStorage))
        assertTrue(SourceLocality.offersLocalCopy(drive))
        assertTrue(SourceLocality.offersLocalCopy(downloads))
    }

    @Test
    fun `a source that cannot be named is offered the copy`() {
        assertTrue(SourceLocality.offersLocalCopy(null))
    }

    @Test
    fun `the override widens the offer to every source`() {
        // Pinned so the switch cannot be flipped by accident: the constant is
        // the documented way to widen the offer, and this is what flipping it
        // has to mean.
        if (SourceLocality.OFFER_FOR_EVERY_SOURCE) {
            assertTrue(SourceLocality.offersLocalCopy(externalStorage))
        } else {
            assertFalse(SourceLocality.offersLocalCopy(externalStorage))
        }
    }
}
