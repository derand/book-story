/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * Availability of a single book image.
 */
@Immutable
sealed interface BookImage {

    /** Not resolved yet — the background pass has not reached this image. */
    data object Loading : BookImage

    /**
     * The encoded (JPEG/PNG/GIF) image, ready to be rendered.
     *
     * Most of a book's images are handed over as *files*: the image loader reads a
     * local file directly, without copying it into a cache of its own, and holds
     * only the *decoded* bitmap — in a memory cache that is bounded and trims
     * itself. Every encoded image held here instead would be a second, unbounded
     * cache next to that one.
     *
     * A small, fixed budget of them is nevertheless kept in memory
     * ([ua.acclorite.book_story.data.cache.ImageMemoryBudget]), because a book
     * whose images all fit in it never has to touch the disk at all — and writing
     * files that are deleted minutes later is work worth skipping when the amount
     * held is bounded either way.
     */
    sealed interface Ready : BookImage {

        /** Held in memory, within the budget. */
        class InMemory(val bytes: ByteArray) : Ready

        /** Written to disk — a parse-cache blob, or a transient session file. */
        class InFile(val file: File) : Ready
    }

    /**
     * The load pass finished without resolving the image: the source book file
     * was deleted or moved, its permission was revoked, or the image is simply
     * not in it anymore.
     */
    data object Missing : BookImage
}

/**
 * Observable store of where the open book's images live, keyed by
 * [ReaderImage.src].
 *
 * Images are resolved in the background so that a parse-cache hit can show the
 * text immediately (the cached text carries image metadata only). This store is
 * the channel through which they reach the reader: the text itself stays
 * immutable, and only the image items currently on screen recompose.
 *
 * It cannot be replaced by mutating [ReaderImage]: [ReaderImage.equals] compares
 * ids only, and an id is restored unchanged from the cache — so putting the image
 * back into the text (a new [ReaderImage], or a rebuilt text list) would leave
 * Compose believing nothing changed.
 *
 * Writes come from a background coroutine; snapshot state handles that.
 */
@Stable
class BookImageStore {

    private val entries = mutableStateMapOf<String, BookImage>()

    /** State of the image [src]; an unknown src reads as [BookImage.Loading]. */
    operator fun get(src: String): BookImage = entries[src] ?: BookImage.Loading

    /** Discards the previous book and starts tracking [srcs], all not resolved yet. */
    fun reset(srcs: Collection<String> = emptyList()) {
        entries.clear()
        srcs.forEach { src -> entries[src] = BookImage.Loading }
    }

    /** Publishes the resolved [image] for [src] to the reader. */
    fun put(src: String, image: BookImage.Ready) {
        entries[src] = image
    }

    /** The srcs that are still not available, i.e. worth (re)loading. */
    fun pending(): Set<String> = entries.entries
        .filter { (_, image) -> image !is BookImage.Ready }
        .map { (src, _) -> src }
        .toSet()

    /**
     * Marks every still-unresolved image as [BookImage.Missing] — call once a
     * load pass has run to completion, so the reader can stop showing them as
     * pending.
     */
    fun finish() {
        entries.keys.toList().forEach { src ->
            if (entries[src] !is BookImage.Ready) entries[src] = BookImage.Missing
        }
    }
}
