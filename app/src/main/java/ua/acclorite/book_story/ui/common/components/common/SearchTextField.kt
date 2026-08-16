/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.components.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import ua.acclorite.book_story.R

/**
 * Search Text Field.
 * Used in main screens for searching.
 *
 * @param modifier Modifier to apply.
 * @param initialQuery Initial search query.
 * @param placeholder Hint shown while the field is empty.
 * @param imeAction What the keyboard's action key does. The reader passes
 * [ImeAction.Done], because there it navigates nothing — it only puts the
 * keyboard away, and calling that "Search" would promise a jump that the
 * arrows, not the keyboard, are in charge of.
 * @param onQueryChange Callback to change query.
 * @param onSearch Search action (refresh list, fetch filtered books etc..).
 */
@OptIn(FlowPreview::class)
@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    initialQuery: String,
    @StringRes placeholder: Int = R.string.search_field_empty,
    imeAction: ImeAction = ImeAction.Search,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboardManager = LocalSoftwareKeyboardController.current

    val query = remember {
        mutableStateOf(initialQuery)
    }

    LaunchedEffect(query) {
        snapshotFlow {
            query.value
        }.debounce(50).collectLatest {
            if (it == initialQuery) return@collectLatest
            onQueryChange(it)
        }
    }


    BasicTextField(
        value = query.value,
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
            lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
            fontFamily = MaterialTheme.typography.titleLarge.fontFamily
        ),
        modifier = modifier,
        onValueChange = {
            query.value = it
        },
        keyboardOptions = KeyboardOptions(
            KeyboardCapitalization.Sentences,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                onQueryChange(query.value)
                onSearch()
                keyboardManager?.hide()
            },
            onDone = {
                onQueryChange(query.value)
                onSearch()
                keyboardManager?.hide()
            }
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant)
    ) { innerText ->
        Box(contentAlignment = Alignment.CenterStart) {
            if (query.value.isEmpty()) {
                StyledText(
                    text = stringResource(id = placeholder),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
            innerText()
        }
    }
}