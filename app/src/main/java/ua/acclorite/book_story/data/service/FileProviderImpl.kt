/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.service

import android.app.Application
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.bookTimingNote
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.service.FileProvider
import javax.inject.Inject

class FileProviderImpl @Inject constructor(
    private val application: Application
) : FileProvider {

    override fun getFileFromBook(book: Book): Result<CachedFile> = runCatchingCancellable {
        val storages = application.contentResolver.persistedUriPermissions
            .map { permission -> CachedFileCompat.fromUri(application, permission.uri) }
            .filter { it.isDirectory }

        storages.forEach { storage ->
            val segments = pathSegmentsUnder(storage.path, book.filePath) ?: return@forEach
            storage.descend(segments)?.let { return@runCatchingCancellable it }
        }

        // Three things the descent cannot follow, all of them properties of a
        // particular documents provider rather than of SAF: a display name
        // containing '/', which it would split into two segments; two children of
        // one directory sharing a name, where it commits to the first while a walk
        // enumerates both subtrees; and a tree whose root reports no path at all,
        // which leaves no route to split. None is reachable on external storage,
        // and none can be tested against a single provider, so the walk stays as
        // the fallback at the price every open used to pay, in the case that would
        // otherwise fail outright.
        //
        // The condition below is the *old* one, deliberately looser than the
        // descent's: a root with no path composes its children as "/<name>" and the
        // import wrote exactly that into the book row, so the walk still matches
        // where the descent had nothing to follow. A fallback that filters more
        // than what it falls back from is not a fallback.
        bookTimingNote { "find file: descent found nothing, walking the tree" }
        storages.forEach { storage ->
            if (!book.filePath.startsWith(storage.path, ignoreCase = true)) return@forEach
            storage.walk().forEach { file ->
                if (book.filePath.equals(file.path, ignoreCase = true)) {
                    return@runCatchingCancellable file
                }
            }
        }

        throw NoSuchElementException("Could not find file from book.")
    }

    /**
     * Walks [segments] down from this directory, one ContentResolver query per
     * level, and returns the file they name — or null if any level is missing, is
     * not a directory where the route needs one, or the route ends on a directory.
     *
     * Each child arrives from the directory cursor with its name already filled in,
     * so matching a segment costs nothing beyond the listing itself.
     */
    private fun CachedFile.descend(segments: List<String>): CachedFile? {
        var current = this
        segments.forEachIndexed { index, segment ->
            val child = current.listFiles().firstOrNull {
                it.name.equals(segment, ignoreCase = true)
            } ?: return null

            if (index == segments.lastIndex) return child.takeIf { !it.isDirectory }
            if (!child.isDirectory) return null
            current = child
        }
        return null
    }

    override fun getStorageFiles(): Result<List<CachedFile>> = runCatchingCancellable {
        application.contentResolver.persistedUriPermissions.mapNotNull { permission ->
            val storage = CachedFileCompat.fromUri(
                application,
                permission.uri
            )
            if (!storage.isDirectory) return@mapNotNull null

            storage
        }.let { storages ->
            storages.filter { storage ->
                storages.none {
                    it.path != storage.path && storage.path.startsWith(
                        it.path,
                        ignoreCase = true
                    )
                }
            }
        }
    }
}

/**
 * The route from [rootPath] down to [filePath] — one display name per element — or
 * null when [filePath] does not name something under [rootPath].
 *
 * This is what lets a book be found by descending instead of by enumerating: a
 * [CachedFile]'s path is composed as `<parent>/<display name>`, so the segments of
 * a path and the names in a directory listing are the same strings by
 * construction.
 *
 * Deliberately strict — a route it refuses is a route the walk would not have
 * matched either, since the walk compares against those same composed paths. In
 * particular a prefix only counts when it ends on a separator, so a tree at
 * `/Books` does not claim `/BooksOld/novel.fb2`; the old code compared prefixes
 * without that boundary and was saved only by the full-path comparison that
 * followed.
 */
internal fun pathSegmentsUnder(rootPath: String, filePath: String): List<String>? {
    val root = rootPath.trim().trimEnd('/')
    val path = filePath.trim().trimEnd('/')

    if (root.isEmpty() || path.length <= root.length) return null
    if (!path.startsWith(root, ignoreCase = true)) return null
    if (path[root.length] != '/') return null

    val segments = path.substring(root.length + 1).split('/')
    // An empty segment means a path no listing can produce ("a//b"), so there is
    // nothing to descend to.
    if (segments.any { it.isBlank() }) return null

    return segments
}