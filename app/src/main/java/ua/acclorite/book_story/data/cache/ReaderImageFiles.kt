/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import android.app.Application
import ua.acclorite.book_story.core.log.logE
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReaderImageFiles"

/**
 * Transient on-disk home for the images of the book being read, for the case
 * where the parse cache cannot hold them as blobs — the cache turned off, or a
 * source that reports neither its size nor its date and so cannot be cached
 * against at all.
 *
 * It exists so the reader can hand the image loader a *file* instead of a byte
 * array: a local file is read directly and not copied into any cache of the
 * loader's own, so the only image memory left is the loader's decoded-bitmap
 * cache, which is bounded and trims itself. Before this, every encoded image of
 * the open book stayed resident for the whole session with nothing to bound it.
 *
 * Layout is `cacheDir/reader_images/<session>/<bookId>/<sha256(src)>`:
 *
 * - **per session** (one directory per process) so [sweep] can delete what other
 *   runs left behind without ever racing a live one;
 * - **per book** so opening one book can drop every *other* book's files in one
 *   step ([keepOnly]) while a reopened book still finds its own ([existing]) —
 *   and so that two books using the same src name ("images/cover.jpg" is not
 *   exotic) never collide.
 *
 * Everything here is best-effort: a file that cannot be written just leaves the
 * image unresolved, exactly like one missing from the book.
 */
@Singleton
class ReaderImageFiles @Inject constructor(application: Application) {

    private val root = File(application.cacheDir, DIR_NAME)

    /** This process's directory — the one [sweep] must not touch. */
    private val session = File(root, UUID.randomUUID().toString())

    /** Writes [bytes] as the image [src] of [bookId], or null if it could not be. */
    fun write(bookId: Int, src: String, bytes: ByteArray): File? {
        val dir = File(session, bookId.toString())
        if (!dir.exists() && !dir.mkdirs()) {
            logE(TAG, "Could not create reader image dir.")
            return null
        }
        val file = File(dir, sha256Hex(src))
        // Temp file then rename: a half-written image must never be published.
        val tmp = File(dir, "${file.name}.tmp")
        return try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            file
        } catch (e: Exception) {
            logE(TAG, "Could not write reader image: ${e.message}")
            tmp.delete()
            null
        }
    }

    /**
     * The files of [bookId] this session already holds, keyed by src — what an
     * earlier open of the same book wrote. Reusing them is the whole point of
     * [keepOnly] retaining them; without this the pass would extract the images
     * again and overwrite identical files.
     */
    fun existing(bookId: Int, srcs: Set<String>): Map<String, File> {
        val dir = File(session, bookId.toString())
        if (!dir.isDirectory) return emptyMap()
        return srcs.mapNotNull { src ->
            val file = File(dir, sha256Hex(src))
            // A missing file measures 0 too, so this covers existence as well;
            // a half-written one cannot be seen (write renames into place).
            if (file.length() > 0) src to file else null
        }.toMap()
    }

    /**
     * Drops the files of every book except [bookId]; call when a reader *opens*.
     *
     * Closing a book deliberately keeps its files. Leaving and re-entering one is
     * an ordinary thing to do — the back gesture is easy to hit by accident — and
     * on an image-heavy book each re-entry would otherwise extract and rewrite
     * tens of megabytes that were on disk all along. They still never outlive the
     * process: the next book taking over drops them, and [sweep] catches the case
     * where the process was killed instead.
     */
    fun keepOnly(bookId: Int) {
        val keep = bookId.toString()
        session.listFiles()
            ?.filter { it.name != keep }
            ?.forEach { it.deleteRecursively() }
    }

    /**
     * Drops one book's files. Unlike closing a book, which keeps them on
     * purpose, this is for a book that is going away: a preview the user looked
     * at and did not keep has no next open to save work for.
     */
    fun drop(bookId: Int) {
        File(session, bookId.toString()).deleteRecursively()
    }

    /**
     * Deletes the leftovers of previous runs. Nothing runs when the process is
     * killed — and swiping the app away from the recents list is an ordinary way
     * to leave a book — so leftovers are the rule rather than an edge case, and
     * app start is the only reliable place to catch them. Waiting for Android to
     * purge `cacheDir` is not enough: that only happens under real storage
     * pressure, so on a roomy device they would pile up for weeks, precisely in
     * the setup where the user asked *not* to cache images.
     */
    fun sweep() {
        root.listFiles()
            ?.filter { it.name != session.name }
            ?.forEach { it.deleteRecursively() }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DIR_NAME = "reader_images"
    }
}
