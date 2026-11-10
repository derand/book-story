/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.navigator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.common.IconButton

/** Opens the tabs' "more" sheet; provided by [NavigatorTabs] for the top bars inside it. */
val LocalOpenNavigatorSheet = staticCompositionLocalOf<() -> Unit> {
    error("NavigatorIconButton is used outside NavigatorTabs.")
}

@Composable
fun NavigatorIconButton(
    onClick: () -> Unit = LocalOpenNavigatorSheet.current
) {
    IconButton(
        icon = Icons.Default.MoreVert,
        contentDescription = R.string.more_content_desc,
        disableOnClick = false
    ) {
        onClick()
    }
}