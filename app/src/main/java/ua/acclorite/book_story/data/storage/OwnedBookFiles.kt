/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.storage

import android.app.Application
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.model.file.CachedFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OwnedBookFiles"

/**
 * The directory books the app owns live in, one directory per book.
 *
 * Deliberately not `books`: `DatabaseHelper.AUTO_MIGRATION_7_8.removeBooksDir`
 * deletes `filesDir/books`, and `AppModule` calls it every time the database is
 * built rather than only when that migration runs. A store named `books` was
 * therefore emptied on every app start — the book was added, survived being
 * closed, and was gone by the next launch.
 */
private const val DIR_NAME = "owned_books"

/**
 * Books the app keeps a copy of, because there is nowhere else to keep them.
 *
 * A library book is normally a path inside a folder the user granted, and the
 * app stores no copy of it: the book stays where the user put it. That fails for
 * a file handed over by another app that exposes no location — the Downloads
 * provider composes its document ids out of MediaStore row numbers, so there is
 * no path to remember and no folder to ask for. Downloading a book is the
 * commonest way to get one, so "cannot be kept" is not an acceptable answer.
 *
 * Deliberately in `filesDir` rather than the cache. The cache is the system's to
 * empty whenever it wants storage back, and it holds three things of ours that
 * are meant to be disposable — the parse cache, the reader's image files and
 * `CachedFile`'s raw copies. A book that vanished from the library because the
 * device got low on space would be a different kind of bug entirely.
 *
 * A directory per book, named by its id, so a book takes its file with it when
 * it is deleted and nothing else has to be consulted to find it. The file keeps
 * its own name inside: the parsers decide the format from the extension.
 */
@Singleton
class OwnedBookFiles @Inject constructor(
    private val application: Application
) {

    private val root: File
        get() = File(application.filesDir, DIR_NAME)

    /** Whether [path] names a file this store owns. */
    fun owns(path: String): Boolean =
        path.isNotBlank() && path.startsWith(root.absolutePath + File.separator)

    /**
     * Copies [source] in and returns the file, or null if it could not be read.
     * A book already stored under [bookId] is replaced.
     */
    fun store(source: CachedFile, bookId: Int): File? {
        val directory = File(root, bookId.toString())
        directory.deleteRecursively()
        if (!directory.mkdirs()) {
            logI(TAG, "Could not make a directory for [$bookId].")
            return null
        }

        val destination = File(directory, source.name)
        try {
            source.openInputStream()?.use { input ->
                destination.outputStream().buffered().use(input::copyTo)
            } ?: throw IllegalStateException("Could not open ${source.name}.")
        } catch (e: Exception) {
            logI(TAG, "Could not store [$bookId]: ${e.message}")
            directory.deleteRecursively()
            return null
        }

        logI(TAG, "Stored ${destination.length()} bytes for [$bookId].")
        return destination
    }

    /** Drops the book's file, if it has one. */
    fun delete(bookId: Int) {
        File(root, bookId.toString()).deleteRecursively()
    }
}
