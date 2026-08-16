/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.reader.model.SearchPosition
import ua.acclorite.book_story.presentation.reader.model.searchPosition
import ua.acclorite.book_story.presentation.reader.model.stepTarget
import ua.acclorite.book_story.ui.common.components.common.IconButton
import ua.acclorite.book_story.ui.common.components.common.SearchTextField
import ua.acclorite.book_story.ui.common.components.common.StyledText

/**
 * The reader's search bar: a query field, where the reader stands among the
 * matches, and the two ways out — back to reading, or staying wherever the
 * search led.
 *
 * There is no "search" key: the arrows navigate, and the keyboard's action key
 * only puts the keyboard away. Every search anywhere else makes Enter mean
 * "forward", and here the reach-for-it direction is back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchBar(
    search: ReaderSearch,
    listState: LazyListState,
    searchQueryChange: (ReaderEvent.OnSearchQueryChange) -> Unit,
    searchStep: (ReaderEvent.OnSearchStep) -> Unit,
    searchVisibility: (ReaderEvent.OnSearchVisibility) -> Unit,
    searchReturn: (ReaderEvent.OnSearchReturn) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Only on the way in: the bar is composed again every time the menu comes
    // back, and a reader who hid it to read a found passage did not ask for the
    // keyboard to return with it.
    LaunchedEffect(Unit) {
        if (search.query.isEmpty()) focusRequester.requestFocus()
    }

    val visible by remember(listState) {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) {
                val index = listState.firstVisibleItemIndex
                index..index
            } else items.first().index..items.last().index
        }
    }
    val position = remember(search.matches, search.current, visible) {
        searchPosition(
            matches = search.matches,
            current = search.current,
            visible = visible
        )
    }
    val canStepBack = remember(search, visible) {
        search.stepTarget(forward = false, visible = visible) != null
    }
    val canStepForward = remember(search, visible) {
        search.stepTarget(forward = true, visible = visible) != null
    }

    TopAppBar(
        navigationIcon = {
            IconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = R.string.search_back_to_reading_content_desc,
                disableOnClick = true
            ) {
                searchReturn(ReaderEvent.OnSearchReturn)
            }
        },
        title = {
            SearchTextField(
                modifier = Modifier.focusRequester(focusRequester),
                initialQuery = search.query,
                placeholder = R.string.search_in_book_field_empty,
                imeAction = ImeAction.Done,
                onQueryChange = { query ->
                    searchQueryChange(ReaderEvent.OnSearchQueryChange(query))
                },
                onSearch = {}
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyledText(
            text = searchStatus(search = search, position = position),
            style = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            ),
            maxLines = 1
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            contentDescription = R.string.search_previous_match_content_desc,
            enabled = canStepBack,
            // Dimmed by hand: the shared button tints its icon the same whether
            // it is enabled or not, so "there is nothing behind you" would be
            // said by a button that looks exactly like a working one.
            color = arrowColor(enabled = canStepBack),
            disableOnClick = false
        ) {
            searchStep(ReaderEvent.OnSearchStep(forward = false))
        }
        IconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = R.string.search_next_match_content_desc,
            enabled = canStepForward,
            color = arrowColor(enabled = canStepForward),
            disableOnClick = false
        ) {
            searchStep(ReaderEvent.OnSearchStep(forward = true))
        }

        TextButton(
            onClick = { searchVisibility(ReaderEvent.OnSearchVisibility(false)) }
        ) {
            StyledText(
                text = stringResource(id = R.string.search_stay_here),
                maxLines = 1
            )
        }
    }
}

/** Material's disabled alpha, applied to the icon the shared button leaves lit. */
private const val DISABLED_ARROW_ALPHA = 0.38f

@Composable
private fun arrowColor(enabled: Boolean): Color = when (enabled) {
    true -> LocalContentColor.current
    false -> LocalContentColor.current.copy(alpha = DISABLED_ARROW_ALPHA)
}

/**
 * What the bar says about the query: the count and where the reader stands in
 * it — on a match, or between two of them, which is the ordinary state before
 * the first jump and which no single number can express.
 */
@Composable
private fun searchStatus(
    search: ReaderSearch,
    position: SearchPosition
): String {
    if (search.scanning) return "…"
    if (search.query.isBlank()) return ""

    val total = search.matches.size
    val counter = when (position) {
        SearchPosition.None -> return stringResource(id = R.string.search_no_matches)
        is SearchPosition.At -> "${position.ordinal} / $total"
        is SearchPosition.Between -> when {
            // Outside the run of matches entirely, which "…1" read as a
            // truncation rather than as a direction.
            position.before <= 0 -> "<1 / $total"
            position.before >= total -> "$total> / $total"
            else -> "${position.before}…${position.before + 1} / $total"
        }
    }

    // A footnote match is highlighted on its reference, so the word itself is
    // nowhere on the page: the bar has to be the one to say where it is.
    if (search.currentMatch?.inNote == true) {
        return "$counter · ${stringResource(id = R.string.search_match_in_note)}"
    }
    return counter
}
