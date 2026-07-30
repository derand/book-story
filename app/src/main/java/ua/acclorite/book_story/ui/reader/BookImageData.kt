/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import ua.acclorite.book_story.domain.model.reader.BookImage
import java.nio.ByteBuffer

/**
 * The image as the loader wants it to be handed over.
 *
 * Neither form involves Coil's disk cache: a local file is read where it lies, and
 * a buffer is memory-only. Both are already the book's own bytes, so a copy of
 * them would be a third place to keep the same image.
 */
internal fun BookImage.Ready.imageData(): Any = when (this) {
    is BookImage.Ready.InFile -> file
    is BookImage.Ready.InMemory -> ByteBuffer.wrap(bytes)
}
