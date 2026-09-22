/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.permission

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.core.log.withoutLocations
import ua.acclorite.book_story.domain.repository.PermissionRepository
import javax.inject.Inject

private const val TAG = "ReleaseUriPermission"

class ReleasePersistableUriPermissionUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository
) {

    suspend operator fun invoke(uri: String) {
        val source = withoutLocations(uri)
        logI(TAG, "Releasing persistable URI permission from \"$source\".")

        permissionRepository.releasePersistableUriPermission(uri).fold(
            onSuccess = {
                logI(TAG, "Successfully released persistable URI permission from \"$source\".")
            },
            onFailure = {
                logE(
                    TAG,
                    "Could not release persistable URI permission from \"$source\" with error: ${it.messageForLog()}."
                )
            }
        )
    }
}