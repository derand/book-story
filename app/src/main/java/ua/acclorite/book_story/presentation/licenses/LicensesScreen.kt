/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.licenses

import android.os.Parcelable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.license_info.LicenseInfoScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.common.SimpleListScaffold
import ua.acclorite.book_story.ui.licenses.data.rememberLicenses
import ua.acclorite.book_story.ui.licenses.licensesItems
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
object LicensesScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val licenses = rememberLicenses()

        SimpleListScaffold(
            title = stringResource(id = R.string.licenses_option),
            onBack = { navigator.pop(from = this) },
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            licensesItems(
                licenses = licenses.value.orEmpty(),
                navigateToLicenseInfo = { navigator.push(LicenseInfoScreen(it.uniqueId)) }
            )
        }
    }
}
