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

    /** Whether the grant changed; a failure is logged and changes nothing. */
    suspend operator fun invoke(uri: String): Boolean {
        val source = withoutLocations(uri)
        logI(TAG, "Releasing persistable URI permission from \"$source\".")

        return permissionRepository.releasePersistableUriPermission(uri).fold(
            onSuccess = {
                logI(TAG, "Successfully released persistable URI permission from \"$source\".")
                true
            },
            onFailure = {
                logE(
                    TAG,
                    "Could not release persistable URI permission from \"$source\" with error: ${it.messageForLog()}."
                )
                false
            }
        )
    }
}