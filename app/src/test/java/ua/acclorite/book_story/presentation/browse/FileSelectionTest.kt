/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.presentation.browse.model.SelectableFile
import ua.acclorite.book_story.presentation.browse.model.toggled
import ua.acclorite.book_story.presentation.browse.model.withSelected

class FileSelectionTest {

    private fun file(uri: String, path: String, selected: Boolean = false) = SelectableFile(
        name = path.substringAfterLast('/'),
        uri = uri,
        path = path,
        size = 0,
        lastModified = 0,
        selected = selected
    )

    private fun List<SelectableFile>.selectedUris() = filter { it.selected }.map { it.uri }

    @Test
    fun `a tap flips only that file, not one whose path it prefixes`() {
        val files = listOf(
            file("u1", "/Books/War.fb2"),
            file("u2", "/Books/War.fb2.zip"),
            file("u3", "/books/war.fb2")
        )

        assertEquals(listOf("u1"), files.toggled("u1").selectedUris())
        assertEquals(emptyList<String>(), files.toggled("u1").toggled("u1").selectedUris())
    }

    @Test
    fun `two files with one path are told apart`() {
        val files = listOf(file("u1", "/storage/a.epub"), file("u2", "/storage/a.epub"))

        assertEquals(listOf("u2"), files.toggled("u2").selectedUris())
    }

    @Test
    fun `selecting a group of one file selects it, even twice`() {
        val files = listOf(file("u1", "/A/a.epub"), file("u2", "/B/b.epub"))
        val group = listOf(files[0])

        assertEquals(listOf("u1"), files.withSelected(group).selectedUris())
        assertEquals(listOf("u1"), files.withSelected(group).withSelected(group).selectedUris())
    }

    @Test
    fun `selecting a group keeps the files outside it as they were`() {
        val files = listOf(
            file("u1", "/A/a.epub"),
            file("u2", "/A/b.epub", selected = true),
            file("u3", "/B/c.epub", selected = true)
        )

        assertEquals(
            listOf("u1", "u2", "u3"),
            files.withSelected(files.take(2)).selectedUris()
        )
        assertEquals(listOf("u2", "u3"), files.withSelected(emptyList()).selectedUris())
    }
}
