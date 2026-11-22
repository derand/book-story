/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.cache.ParseCache
import javax.inject.Inject

class ClearParseCacheUseCase @Inject constructor(
    private val parseCache: ParseCache
) {

    /** Deletes every entry with its image files, on IO. */
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        parseCache.clear()
    }
}
