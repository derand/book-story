/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.common.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R

/**
 * One row per sort order in [entries], titled by the string resource [label] gives
 * for it, an arrow on the [selected] one. Tapping it flips [descending]; tapping
 * another selects that one, descending.
 */
fun <T : Enum<T>> LazyListScope.SortOrderList(
    entries: List<T>,
    label: (T) -> Int,
    selected: T,
    descending: Boolean,
    onChange: (sortOrder: T, descending: Boolean) -> Unit
) {
    items(entries, key = { it.name }) { item ->
        val isSelected = item == selected

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isSelected) onChange(item, !descending)
                    else onChange(item, true)
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (descending) Icons.Default.ArrowDownward
                else Icons.Default.ArrowUpward,
                contentDescription = stringResource(id = R.string.sort_order_content_desc),
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.secondary
                else Color.Transparent
            )
            Spacer(modifier = Modifier.width(24.dp))

            StyledText(
                text = stringResource(id = label(item)),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
        }
    }
}
