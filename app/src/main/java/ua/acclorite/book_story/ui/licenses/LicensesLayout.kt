/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.licenses

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.mikepenz.aboutlibraries.entity.Library

/** Licenses' list: one item per library. */
fun LazyListScope.licensesItems(
    licenses: List<Library>,
    navigateToLicenseInfo: (Library) -> Unit
) {
    items(
        licenses,
        key = { it.uniqueId }
    ) { library ->
        LicensesItem(library = library) {
            navigateToLicenseInfo(library)
        }
    }
}
