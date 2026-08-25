/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.file_system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.domain.service.FileProvider
import javax.inject.Inject

private const val TAG = "GetBookSources"

/**
 * Every place the user granted, in the order they granted them, each with
 * whether it answered just now.
 *
 * Asking costs one query per source, and against a provider that is not on this
 * device that is a round trip — hence off the main thread, and hence asked when
 * a screen opens or a grant changes rather than on every recomposition.
 */
class GetBookSourcesUseCase @Inject constructor(
    private val fileProvider: FileProvider
) {

    suspend operator fun invoke(): List<BookSource> = withContext(Dispatchers.IO) {
        fileProvider.getStorageSources().fold(
            onSuccess = { it },
            onFailure = {
                logE(TAG, "Could not read the granted sources: ${it.message}")
                emptyList()
            }
        )
    }
}
