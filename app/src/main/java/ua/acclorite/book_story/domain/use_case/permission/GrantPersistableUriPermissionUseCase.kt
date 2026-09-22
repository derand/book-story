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

private const val TAG = "GrantUriPermission"

class GrantPersistableUriPermissionUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository
) {

    suspend operator fun invoke(uri: String) {
        val source = withoutLocations(uri)
        logI(TAG, "Granting persistable URI permission to \"$source\".")

        permissionRepository.grantPersistableUriPermission(uri).fold(
            onSuccess = {
                logI(TAG, "Successfully granted persistable URI permission to \"$source\".")
            },
            onFailure = {
                logE(
                    TAG,
                    "Could not grant persistable URI permission to \"$source\" with error: ${it.messageForLog()}."
                )
            }
        )
    }
}