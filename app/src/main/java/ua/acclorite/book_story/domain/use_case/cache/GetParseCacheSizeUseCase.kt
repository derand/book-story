/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.cache

import ua.acclorite.book_story.data.cache.ParseCache
import javax.inject.Inject

class GetParseCacheSizeUseCase @Inject constructor(
    private val parseCache: ParseCache
) {

    operator fun invoke(): Long = parseCache.totalSizeBytes()
}
