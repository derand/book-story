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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.license_info.LicenseInfoScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.top_bar.collapsibleTopAppBarScrollBehavior
import ua.acclorite.book_story.ui.licenses.LicensesContent
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
object LicensesScreen : Screen, Parcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current

        val (scrollBehavior, listState) = TopAppBarDefaults.collapsibleTopAppBarScrollBehavior(
            listState = rememberLazyListState()
        )
        val licenses = remember {
            derivedStateOf {
                Libs.Builder().withJson(context, R.raw.aboutlibraries).build()
                    .libraries.sortedBy { it.openSource }
            }
        }

        LicensesContent(
            licenses = licenses.value,
            scrollBehavior = scrollBehavior,
            listState = listState,
            navigateToLicenseInfo = {
                navigator.push(LicenseInfoScreen(it.uniqueId))
            },
            navigateBack = {
                navigator.pop()
            }
        )
    }
}