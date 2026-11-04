/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.about.data

import kotlinx.collections.immutable.persistentListOf
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.about.model.Badge

object AboutData {
    val badges = persistentListOf(
        Badge(
            id = "github",
            drawable = R.drawable.github,
            imageVector = null,
            contentDescription = R.string.github_badge,
            url = "https://github.com/derand"
        ),
        Badge(
            id = "tryzub",
            drawable = R.drawable.tryzub,
            imageVector = null,
            contentDescription = R.string.tryzub_badge,
            url = null
        )
    )
}