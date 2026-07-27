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

/**
 * Availability of a single book image's encoded bytes.
 */
@Immutable
sealed interface BookImage {

    /** Bytes are not loaded yet — the background pass has not reached this image. */
    data object Loading : BookImage

    /** Encoded (JPEG/PNG/GIF) bytes, ready to be rendered. */
    class Ready(val bytes: ByteArray) : BookImage

    /**
     * The load pass finished without resolving the bytes: the source book file
     * was deleted or moved, its permission was revoked, or the image is simply
     * not in it anymore.
     */
    data object Missing : BookImage
}

/**
 * Observable store of the open book's image bytes, keyed by [ReaderImage.src].
 *
 * Images are loaded in the background so that a parse-cache hit can show the text
 * immediately (the cached text carries image metadata only). This store is the
 * channel through which the loaded bytes reach the reader: the text itself stays
 * immutable, and only the image items currently on screen recompose.
 *
 * It cannot be replaced by mutating [ReaderImage]: [ReaderImage.equals] compares
 * ids only, and an id is restored unchanged from the cache — so putting the bytes
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

    /** Discards the previous book and starts tracking [srcs], all not loaded yet. */
    fun reset(srcs: Collection<String> = emptyList()) {
        entries.clear()
        srcs.forEach { src -> entries[src] = BookImage.Loading }
    }

    /** Publishes the loaded [bytes] of [src] to the reader. */
    fun put(src: String, bytes: ByteArray) {
        entries[src] = BookImage.Ready(bytes)
    }

    /** The srcs whose bytes are still not available, i.e. worth (re)loading. */
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
