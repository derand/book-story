/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.ui.common.components.top_bar.collapsibleTopAppBarScrollBehavior
import ua.acclorite.book_story.ui.navigator.NavigatorBackIconButton

/**
 * A large collapsing top bar with [title], a back arrow and [actions], over one
 * scrolling list of [content]. Settings, About, Credits, Licenses and License info
 * are all this, so they share one set of insets (navigation bar, keyboard) and one
 * top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleListScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit
) {
    val (scrollBehavior, listState) = TopAppBarDefaults.collapsibleTopAppBarScrollBehavior()

    Scaffold(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    StyledText(title)
                },
                navigationIcon = {
                    NavigatorBackIconButton(navigateBack = onBack)
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumnWithScrollbar(
            Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
