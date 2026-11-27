/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.license_info

import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.common.IconButton
import ua.acclorite.book_story.ui.common.components.common.SimpleListScaffold
import ua.acclorite.book_story.ui.common.helpers.openInBrowser
import ua.acclorite.book_story.ui.license_info.licenseInfoItems
import ua.acclorite.book_story.ui.licenses.data.rememberLicenses
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
data class LicenseInfoScreen(val uniqueId: String) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val activity = checkNotNull(LocalActivity.current)
        val libraries = rememberLicenses()
        val library = libraries.value?.find { it.uniqueId == uniqueId }

        SimpleListScaffold(
            title = library?.name.orEmpty(),
            onBack = { navigator.pop(from = this) },
            actions = {
                val website = library?.website
                if (!website.isNullOrBlank()) {
                    IconButton(
                        icon = Icons.Outlined.Language,
                        contentDescription = R.string.open_in_web_content_desc,
                        disableOnClick = false
                    ) {
                        activity.openInBrowser(website)
                    }
                }
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            licenseInfoItems(library = library, loaded = libraries.value != null)
        }
    }
}
