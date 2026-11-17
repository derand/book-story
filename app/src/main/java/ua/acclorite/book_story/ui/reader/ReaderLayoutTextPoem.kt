/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Poem
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

/**
 * An FB2 poem laid out as one block: as wide as its longest line, shifted one
 * indent step from the left edge of the page, left-aligned inside. Role
 * indents ([ReaderTextRole]) apply within the block, so they stay proportional
 * to the poem itself no matter how long its lines are.
 */
@Composable
fun LazyItemScope.ReaderLayoutTextPoem(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    poem: Poem,
    look: ReaderTextLook,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    val poemFontSize = look.fontSize * POEM_FONT_SCALE
    val poemLineHeight = look.lineHeight * POEM_FONT_SCALE
    val authorExtraIndent = with(LocalDensity.current) { poemFontSize.toDp() }

    Column(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = look.sidePadding)
            .padding(start = BLOCK_INDENT_STEP),
        horizontalAlignment = Alignment.Start
    ) {
        // Verse lines are set tight (no inter-paragraph spacing) — the gap
        // between stanzas comes from the poem's own blank lines
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.Start
        ) {
            poem.lines.forEachIndexed { part, line ->
                StyledText(
                    text = remember(
                        line.line, openNote, part, searchMatches, currentSearchMatch, look.fontColor
                    ) {
                        line.line
                            .withReferenceListeners { tag ->
                                openNote(ReaderEvent.OnOpenNote(tag))
                            }
                            .withSearchHighlights(
                                matches = searchMatches,
                                current = currentSearchMatch,
                                fontColor = look.fontColor,
                                part = part
                            )
                    },
                    modifier = Modifier
                        .padding(
                            start = BLOCK_INDENT_STEP * line.role.indentSteps +
                                    if (line.role == ReaderTextRole.TextAuthor) {
                                        // One extra (current) character height
                                        authorExtraIndent
                                    } else 0.dp
                        ),
                    style = TextStyle(
                        fontFamily = look.fontFamily.font,
                        fontWeight = look.fontThickness.thickness,
                        textAlign = TextAlign.Start,
                        fontStyle = look.fontStyle,
                        letterSpacing = look.letterSpacing,
                        fontSize = poemFontSize,
                        lineHeight = poemLineHeight,
                        color = look.fontColor,
                        lineBreak = LineBreak.Paragraph
                    ),
                    highlightText = look.highlightedReading,
                    highlightThickness = look.highlightedReadingThickness
                )
            }
        }
    }
}
