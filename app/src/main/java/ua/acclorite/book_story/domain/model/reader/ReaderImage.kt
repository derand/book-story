/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.runtime.Immutable

/**
 * Image of the book in it's compressed (encoded) form.
 * Pixels are decoded lazily by the reader only when the image
 * becomes visible, [width] and [height] allow reserving the final
 * layout space before that happens.
 *
 * @param id Stable cache key, unique per image content.
 * @param bytes Encoded image (JPEG/PNG/GIF) bytes.
 */
@Immutable
class ReaderImage(
    val id: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int
) {
    val aspectRatio = width.toFloat() / height.toFloat()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderImage) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
