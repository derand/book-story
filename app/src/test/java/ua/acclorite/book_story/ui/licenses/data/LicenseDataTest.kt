/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.licenses.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Licenses and License info read one parsed copy of the bundled licence list,
 * so the ~128 KB JSON is parsed once per process.
 */
@RunWith(RobolectricTestRunner::class)
class LicenseDataTest {

    /** Robolectric does not see the app's resources here, so the file is read from source. */
    private var reads = 0
    private val readJson = {
        reads++
        File("src/main/res/raw/aboutlibraries.json").readText()
    }

    @Test
    fun `the bundled list parses, closed-source libraries first`() = runBlocking {
        val libraries = LicenseData.load(readJson)

        assertTrue(libraries.isNotEmpty())
        assertTrue(libraries.zipWithNext().all { (a, b) -> !a.openSource || b.openSource })
    }

    @Test
    fun `a second load returns the same list without reading again`() = runBlocking {
        val first = LicenseData.load(readJson)
        val readsAfterFirst = reads

        assertSame(first, LicenseData.cached)
        assertSame(first, LicenseData.load(readJson))
        assertEquals(readsAfterFirst, reads)
    }
}
