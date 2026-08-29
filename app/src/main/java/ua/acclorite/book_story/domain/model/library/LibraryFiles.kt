/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

/**
 * What the library already holds, in the two terms a listed file answers to:
 * the identity its provider gives it, and its path.
 *
 * Browse lists files, not books, so this is what keeps a book the user already
 * has from being offered again. The rule is written about the *file* a row
 * stands for, which is not always the file the row points at: a book the app
 * keeps a copy of points into the app's own directory, which no listing can
 * reach, while the file it must be recognised by is the original it was taken
 * from — the one `origin*` records.
 *
 * Both are therefore asked of every book, and a copy answers with its origin.
 * A copy answering with nothing is how the same cloud document came to be added
 * once per copy made of it.
 */
class LibraryFiles(
    private val identities: Set<String>,
    private val paths: Set<String>
) {

    /** Whether the library already holds the file so described. */
    fun holds(filePath: String, documentAuthority: String?, documentId: String?): Boolean {
        identityKey(documentAuthority, documentId)?.let {
            if (it in identities) return true
        }
        return filePath.lowercase() in paths
    }

    companion object {
        internal fun identityKey(authority: String?, documentId: String?): String? =
            documentId?.let { "$authority|$it" }
    }
}

/** The index [LibraryFiles] answers from, built once per listing. */
fun List<Book>.libraryFiles(): LibraryFiles = LibraryFiles(
    identities = flatMap {
        listOfNotNull(it.currentIdentity, it.originIdentity)
    }.toSet(),
    paths = mapNotNull { it.listedPath?.lowercase() }.toSet()
)

private val Book.currentIdentity: String?
    get() = LibraryFiles.identityKey(documentAuthority, documentId)

private val Book.originIdentity: String?
    get() = LibraryFiles.identityKey(originAuthority, originDocumentId)

/**
 * The path a listing could show this book at, or null when none could.
 *
 * Asked only of a book no identity answers for — every row written before one
 * was stored, until the first time it is opened, and a copy whose origin was a
 * source that names no document. A path is the weaker term and stays the
 * fallback: two folders on one cloud provider invent every path under them from
 * the same string, so a path is trusted only where an identity is absent.
 *
 * A copy's own path is never it: it is in the app's own directory, which is
 * listed nowhere.
 */
private val Book.listedPath: String?
    get() = when {
        isOwnCopy -> originPath?.takeIf { originDocumentId == null }
        documentId == null -> filePath
        else -> null
    }
