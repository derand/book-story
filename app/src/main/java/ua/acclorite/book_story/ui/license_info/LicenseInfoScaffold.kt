/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.license_info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.entity.Library
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.placeholder.ErrorPlaceholder
import ua.acclorite.book_story.ui.theme.DefaultTransition

/**
 * @param library The library to show, null while the list loads or if it is not in the list.
 * @param loaded Whether the licence list has been read, so a null [library] means it is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseInfoScaffold(
    library: Library?,
    loaded: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
    navigateToBrowserPage: (String) -> Unit,
    navigateBack: () -> Unit
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LicenseInfoTopBar(
                library = library,
                scrollBehavior = scrollBehavior,
                navigateToBrowserPage = navigateToBrowserPage,
                navigateBack = navigateBack
            )
        }
    ) { paddingValues ->
        DefaultTransition(library != null) {
            if (library != null) {
                LicenseInfoLayout(
                    library = library,
                    paddingValues = paddingValues,
                    listState = listState
                )
            }
        }
        if (loaded && library == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                ErrorPlaceholder(
                    errorMessage = stringResource(id = R.string.error_license_not_found),
                    icon = painterResource(id = R.drawable.error)
                )
            }
        }
    }
}
