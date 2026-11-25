/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.ui.common.components.common.SimpleListScaffold
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/** A settings screen: [SimpleListScaffold] with a string-resource [title]. */
@Composable
fun SettingsSubScreen(
    @StringRes title: Int,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    val navigator = LocalNavigator.current

    SimpleListScaffold(
        title = stringResource(id = title),
        onBack = { navigator.pop() },
        contentPadding = contentPadding,
        content = content
    )
}
