/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The percentage shown on library cards, Book info and in the reader. It
 * follows the app language, which is the default locale: under Arabic the
 * digits and the separator are not Latin, and a whole number must still lose
 * its zero fraction.
 */
class CalculateProgressTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun Float.inLocale(tag: String, digits: Int): String {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return calculateProgress(digits)
    }

    @Test
    fun `a whole number has no fraction in any language`() {
        assertEquals("50", 0.5f.inLocale("en", 1))
        assertEquals("50", 0.5f.inLocale("uk", 2))
        assertEquals("٥٠", 0.5f.inLocale("ar", 1))
        assertEquals("١٠٠", 1f.inLocale("ar", 2))
        assertEquals("٠", 0f.inLocale("ar", 1))
    }

    @Test
    fun `the fraction keeps the language's separator`() {
        assertEquals("45.9", 0.459f.inLocale("en", 1))
        assertEquals("45,9", 0.459f.inLocale("uk", 1))
        assertEquals("٤٥٫٩", 0.459f.inLocale("ar", 1))
    }

    @Test
    fun `trailing zeros of the fraction go`() {
        assertEquals("7.3", 0.073f.inLocale("en", 2))
        assertEquals("7,3", 0.073f.inLocale("uk", 2))
        assertEquals("15.28", 0.1528f.inLocale("en", 2))
    }

    @Test
    fun `rounds half up to the digits asked`() {
        assertEquals("12.35", 0.123456f.inLocale("en", 2))
        assertEquals("13", 0.125f.inLocale("en", 0))
        assertEquals("1", 0.005f.inLocale("en", 0))
    }

    @Test
    fun `no minus sign below zero`() {
        assertEquals("0", (-0f).inLocale("en", 1))
        assertEquals("0", (-0.00001f).inLocale("en", 1))
    }
}
