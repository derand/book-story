/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import ua.acclorite.book_story.domain.model.reader.BookImageStore

/** Store used outside the reader (and in previews): every image reads as pending. */
private val EmptyBookImageStore = BookImageStore()

/**
 * Bytes of the open book's images, filled in by a background load.
 *
 * Provided as a composition local rather than passed down, because the only
 * consumer sits four layers deep (ReaderScaffold → ReaderLayout → ReaderLayoutText
 * → ReaderLayoutTextImage).
 */
val LocalBookImages = staticCompositionLocalOf { EmptyBookImageStore }

@Composable
fun ProvideBookImages(store: BookImageStore, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBookImages provides store) {
        content()
    }
}
