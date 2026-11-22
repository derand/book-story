/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.file_system

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.repository.FileSystemRepository
import javax.inject.Inject

private const val TAG = "GetFiles"

class GetFilesUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) {

    /**
     * Every book file under the granted folders, or a failure: never an empty
     * list in its place. A full walk, so asked for only when the folders may
     * have changed; Browse narrows the result itself.
     */
    suspend operator fun invoke(): Result<List<File>> {
        logI(TAG, "Walking the granted folders.")
        return fileSystemRepository.listBookFiles()
            .onSuccess {
                logI(TAG, "Successfully found [${it.size}] files.")
            }.onFailure {
                logE(TAG, "Could not find files with error: ${it.messageForLog()}")
            }
    }
}
