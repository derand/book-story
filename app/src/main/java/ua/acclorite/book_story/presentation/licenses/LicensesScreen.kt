/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.licenses

import android.os.Parcelable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.license_info.LicenseInfoScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.top_bar.collapsibleTopAppBarScrollBehavior
import ua.acclorite.book_story.ui.licenses.LicensesContent
import ua.acclorite.book_story.ui.licenses.data.rememberLicenses
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
object LicensesScreen : Screen, Parcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        val (scrollBehavior, listState) = TopAppBarDefaults.collapsibleTopAppBarScrollBehavior(
            listState = rememberLazyListState()
        )
        val licenses = rememberLicenses()

        LicensesContent(
            licenses = licenses.value.orEmpty(),
            scrollBehavior = scrollBehavior,
            listState = listState,
            navigateToLicenseInfo = {
                navigator.push(LicenseInfoScreen(it.uniqueId))
            },
            navigateBack = {
                navigator.pop(from = this)
            }
        )
    }
}