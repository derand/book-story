/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.data

import kotlinx.collections.immutable.persistentListOf

object ExtensionsData {
    val fileExtensions = persistentListOf(
        ".epub",
        ".pdf",
        ".fb2",
        ".txt",
        ".html",
        ".htm",
        ".md"
    )

    val imageExtensions = persistentListOf(
        ".png",
        ".jpg",
        ".jpeg",
        ".gif"
    )

    /**
     * Which of [fileExtensions] this file name names, or null if it names none.
     *
     * The last extension, and failing that the **second-to-last** — because
     * Google Drive saves an FB2 as `book.fb2.xml`, and that is how books arrive
     * on a device. Nothing is read: this is a question about the name.
     *
     * The position is the whole of the care here. Only the second-to-last
     * counts, never a known extension found anywhere in the name, or
     *
     *     як конвертувати .fb2 в .epub.html.xml
     *
     * — an HTML page about converting books — would be handed to the FB2 parser.
     * Its second-to-last is `.html`, which is what it is. By the same rule
     * `book.fb2.xml.bak` is not a book: two unknown extensions is a file that
     * has stopped saying what it holds.
     *
     * A consequence taken deliberately: `book.epub.bak` opens as EPUB, and shows
     * up in Browse for the same reason.
     */
    fun formatOf(fileName: String): String? {
        val parts = fileName.split('.')
        if (parts.size < 2) return null

        fun extensionAt(index: Int) = ".${parts[index]}".lowercase().trim()

        extensionAt(parts.lastIndex).let { if (it in fileExtensions) return it }
        if (parts.size < 3) return null

        return extensionAt(parts.lastIndex - 1).takeIf { it in fileExtensions }
    }
}