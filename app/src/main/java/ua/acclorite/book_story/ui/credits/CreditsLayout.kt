/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.credits

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import ua.acclorite.book_story.ui.credits.data.CreditData

/** Credits' list: one item per credited project. */
fun LazyListScope.creditsItems(
    navigateToBrowserPage: (String) -> Unit
) {
    items(
        CreditData.credits,
        key = { it.name }
    ) { credit ->
        CreditItem(credit = credit) {
            credit.website?.let { website ->
                navigateToBrowserPage(website)
            }
        }
    }
}
