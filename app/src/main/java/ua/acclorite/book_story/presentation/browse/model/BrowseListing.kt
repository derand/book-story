/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse.model

import ua.acclorite.book_story.core.data.ExtensionsData
import ua.acclorite.book_story.core.helpers.compareByWithOrder
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.library.LibraryFiles

/** How the user asked Browse to narrow and order what it lists. */
data class BrowseListOptions(
    val query: String = "",
    /** Extensions to keep; empty keeps every file. */
    val includedExtensions: List<String> = emptyList(),
    val sortOrder: BrowseSortOrder = BrowseSortOrder.LAST_MODIFIED,
    val descending: Boolean = true
)

/**
 * What Browse shows out of the last walk: the files the library does not hold
 * yet, matching the search and the extension filter, in the order chosen.
 *
 * All of it is done over the walk in memory. Each of these changes far more
 * often than the folders do — every pause in typing, every book added — and a
 * walk is a query per directory, over the network for a cloud folder.
 */
fun List<File>.browseListing(existing: LibraryFiles, options: BrowseListOptions): List<File> {
    val query = options.query.trim()
    return filter { file ->
        (query.isEmpty() || file.name.contains(query, ignoreCase = true)) &&
                (options.includedExtensions.isEmpty() ||
                        options.includedExtensions.any { file.path.endsWith(it, ignoreCase = true) }) &&
                !existing.holds(
                    filePath = file.path,
                    documentAuthority = file.documentAuthority,
                    documentId = file.documentId
                )
    }.sortedWith(
        compareByWithOrder(options.descending) { file ->
            when (options.sortOrder) {
                BrowseSortOrder.NAME -> file.name.trim()

                // The format the file will actually be read as, so a book
                // named `*.fb2.xml` sorts with the other FB2s rather than
                // under "xml".
                BrowseSortOrder.FILE_FORMAT -> ExtensionsData.formatOf(file.name).orEmpty()

                BrowseSortOrder.FILE_SIZE -> file.size

                else -> file.lastModified
            }
        }
    )
}
