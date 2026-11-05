/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.core.ui.MonospaceFontFamily
import ua.acclorite.book_story.domain.model.reader.ReaderText.Preformatted
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.ui.common.components.common.StyledText

/** Relative, so it tracks the reader font size. */
private const val PREFORMATTED_FONT_SCALE = 0.875f

/** Of the font colour: the background then works on every reader theme. */
private const val PREFORMATTED_BACKGROUND_ALPHA = 0.08f

/**
 * A code block: one panel, in a monospace font, whatever the reader's own font
 * is. Lines keep to the start of the block and wrap at its edge. Alignment,
 * letter spacing and highlighted reading are left out: all three would take
 * columns of characters apart.
 */
@Composable
fun LazyItemScope.ReaderLayoutTextPreformatted(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    preformatted: Preformatted,
    fontColor: Color,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    fontSize: TextUnit,
    sidePadding: Dp,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    Column(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .background(
                color = fontColor.copy(alpha = PREFORMATTED_BACKGROUND_ALPHA),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        preformatted.lines.forEachIndexed { part, line ->
            StyledText(
                text = remember(
                    line, openNote, part, searchMatches, currentSearchMatch, fontColor
                ) {
                    line
                        .withReferenceListeners { tag ->
                            openNote(ReaderEvent.OnOpenNote(tag))
                        }
                        .withSearchHighlights(
                            matches = searchMatches,
                            current = currentSearchMatch,
                            fontColor = fontColor,
                            part = part
                        )
                },
                style = TextStyle(
                    fontFamily = MonospaceFontFamily,
                    fontWeight = fontThickness.thickness,
                    textAlign = TextAlign.Start,
                    fontStyle = fontStyle,
                    fontSize = fontSize * PREFORMATTED_FONT_SCALE,
                    lineHeight = lineHeight * PREFORMATTED_FONT_SCALE,
                    color = fontColor,
                    lineBreak = LineBreak.Simple
                )
            )
        }
    }
}
