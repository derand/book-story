/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.license_info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.entity.Library
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.placeholder.ErrorPlaceholder

/**
 * License info's list: one expandable item per licence of [library], or an error
 * once the list is [loaded] and [library] is not in it.
 */
fun LazyListScope.licenseInfoItems(
    library: Library?,
    loaded: Boolean
) {
    if (library == null) {
        if (loaded) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorPlaceholder(
                        errorMessage = stringResource(id = R.string.error_license_not_found),
                        icon = painterResource(id = R.drawable.error)
                    )
                }
            }
        }
        return
    }

    items(
        library.licenses.toList(),
        key = { it.name }
    ) { license ->
        LicenseInfoItem(license = license)
    }
}
