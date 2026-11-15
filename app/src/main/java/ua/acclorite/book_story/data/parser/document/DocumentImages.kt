/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

import android.util.Base64
import ua.acclorite.book_story.data.parser.decodeHref

/**
 * Where the images of one document come from. [DocumentParser] hands it the raw
 * reference of an `<img src>` or `<image href>` and gets back a key, which
 * becomes [ua.acclorite.book_story.domain.model.reader.ReaderImage.src] — the
 * key the parse cache and [ua.acclorite.book_story.data.parser.image.BookImageLoader]
 * find the bytes by again.
 */
interface DocumentImages {

    /** The key of the image [ref] points to, or null if the book has no such image. */
    fun resolve(ref: String): String?

    /** The encoded bytes of the image under [key], or null if they cannot be read. */
    fun bytes(key: String): ByteArray?
}

/**
 * FB2's images: Base64 `<binary id="…">`, referenced as `#id`. Keyed by the
 * lowercased id, which is also what the loader matches a `<binary>` by.
 */
class Base64Images(private val binaries: Map<String, String>) : DocumentImages {

    override fun resolve(ref: String): String? =
        decodeHref(ref.removePrefix("#").substringAfterLast('/'))
            .lowercase()
            .takeIf { it in binaries }

    override fun bytes(key: String): ByteArray? =
        binaries[key]?.let { encoded -> Base64.decode(encoded, Base64.DEFAULT) }
}
