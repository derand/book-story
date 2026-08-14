/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.file_system

import ua.acclorite.book_story.core.data.ExtensionsData
import ua.acclorite.book_story.core.helpers.compareByWithOrder
import ua.acclorite.book_story.core.helpers.runCatchingCancellable
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.repository.FileSystemRepository
import ua.acclorite.book_story.presentation.browse.model.BrowseSortOrder
import javax.inject.Inject

private const val TAG = "GetFiles"

class GetFilesUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository,
    private val settings: SettingsManager
) {

    suspend operator fun invoke(query: String = ""): List<File> {
        logI(TAG, "Searching for files with query: \"$query\".")

        fun List<File>.filterFiles(): List<File> {
            return if (settings.browseIncludedFilterItems.lastValue.isEmpty()) this
            else filter { file ->
                settings.browseIncludedFilterItems.lastValue.any { extension ->
                    file.path.endsWith(
                        extension, ignoreCase = true
                    )
                }
            }
        }

        fun List<File>.sortFiles(): List<File> {
            return sortedWith(
                compareByWithOrder(settings.browseSortOrderDescending.lastValue) { file ->
                    when (settings.browseSortOrder.lastValue) {
                        BrowseSortOrder.NAME -> file.name.trim()

                        // The format the file will actually be read as, so a
                        // book named `*.fb2.xml` sorts with the other FB2s
                        // rather than under "xml".
                        BrowseSortOrder.FILE_FORMAT ->
                            ExtensionsData.formatOf(file.name).orEmpty()

                        BrowseSortOrder.FILE_SIZE -> file.size

                        else -> file.lastModified
                    }
                }
            )
        }

        return runCatchingCancellable {
            fileSystemRepository.searchFiles(query)
                .getOrThrow()
                .filterFiles()
                .sortFiles()
        }.fold(
            onSuccess = {
                logI(TAG, "Successfully found [${it.size}] files.")
                it
            },
            onFailure = {
                logE(TAG, "Could not find files with error: ${it.message}")
                emptyList()
            }
        )
    }
}