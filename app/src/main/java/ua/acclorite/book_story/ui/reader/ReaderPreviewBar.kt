/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.theme.readerBarsColor

/**
 * Shown for as long as the book is only being previewed, and never once it is
 * kept.
 *
 * Deliberately not part of the reader's menu, which is the natural place for it
 * and the wrong one: the menu hides on the first tap into the text, so the one
 * decision this whole screen exists for would be two taps away and out of sight
 * for a reader who never opens the menu. It costs a strip of the page, and it
 * costs it only while the question is open.
 */
@Composable
fun ReaderPreviewBar(
    addToLibrary: (ReaderEvent.OnAddToLibrary) -> Unit,
    onHeightChanged: (Dp) -> Unit
) {
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The reader's list has to end above this bar rather than behind it:
            // the bar is here for the whole preview, so text scrolled under it
            // would simply never be readable. Measured rather than assumed —
            // the button grows with the font scale.
            .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
            .background(MaterialTheme.colorScheme.readerBarsColor)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StyledText(
            text = stringResource(id = R.string.preview_bar_title),
            style = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1
        )

        Button(onClick = { addToLibrary(ReaderEvent.OnAddToLibrary) }) {
            StyledText(
                text = stringResource(id = R.string.add_to_library_content_desc),
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onPrimary
                ),
                maxLines = 1
            )
        }
    }
}
