/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.credits

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.components.common.SimpleListScaffold
import ua.acclorite.book_story.ui.common.helpers.LocalActivity
import ua.acclorite.book_story.ui.common.helpers.openInBrowser
import ua.acclorite.book_story.ui.credits.creditsItems
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
object CreditsScreen : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val activity = LocalActivity.current

        SimpleListScaffold(
            title = stringResource(id = R.string.credits_option),
            onBack = { navigator.pop(from = this) }
        ) {
            creditsItems(navigateToBrowserPage = activity::openInBrowser)
        }
    }
}
