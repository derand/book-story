/*
 * Book's Story — free and open-source Material You eBook reader.
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Poem
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.helpers.noRippleClickable
import ua.acclorite.book_story.ui.reader.model.FontWithName

/**
 * An FB2 poem laid out as one block: as wide as its longest line, shifted one
 * indent step from the left edge of the page, left-aligned inside. Role
 * indents ([ReaderTextRole]) apply within the block, so they stay proportional
 * to the poem itself no matter how long its lines are.
 */
@Composable
fun LazyItemScope.ReaderLayoutTextPoem(
    poem: Poem,
    showMenu: Boolean,
    fontFamily: FontWithName,
    fontColor: Color,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    sidePadding: Dp,
    doubleClickTranslation: Boolean,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    toolbarHidden: Boolean,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit
) {
    val poemFontSize = fontSize * POEM_FONT_SCALE
    val poemLineHeight = lineHeight * POEM_FONT_SCALE
    val authorExtraIndent = with(LocalDensity.current) { poemFontSize.toDp() }

    Column(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .padding(start = BLOCK_INDENT_STEP),
        horizontalAlignment = Alignment.Start
    ) {
        // Verse lines are set tight (no inter-paragraph spacing) — the gap
        // between stanzas comes from the poem's own blank lines
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.Start
        ) {
            poem.lines.forEach { line ->
                StyledText(
                    text = remember(line.line, openNote) {
                        line.line.withReferenceListeners { tag ->
                            openNote(ReaderEvent.OnOpenNote(tag))
                        }
                    },
                    modifier = Modifier
                        .padding(
                            start = BLOCK_INDENT_STEP * line.role.indentSteps +
                                    if (line.role == ReaderTextRole.TextAuthor) {
                                        // One extra (current) character height
                                        authorExtraIndent
                                    } else 0.dp
                        )
                        .then(
                            if (doubleClickTranslation && toolbarHidden) {
                                Modifier.noRippleClickable(
                                    onDoubleClick = {
                                        openTranslator(
                                            ReaderEvent.OnOpenTranslator(
                                                textToTranslate = line.line.text,
                                                translateWholeParagraph = true
                                            )
                                        )
                                    },
                                    onClick = {
                                        menuVisibility(
                                            ReaderEvent.OnMenuVisibility(
                                                show = !showMenu,
                                                saveCheckpoint = true
                                            )
                                        )
                                    }
                                )
                            } else Modifier
                        ),
                    style = TextStyle(
                        fontFamily = fontFamily.font,
                        fontWeight = fontThickness.thickness,
                        textAlign = TextAlign.Start,
                        fontStyle = fontStyle,
                        letterSpacing = letterSpacing,
                        fontSize = poemFontSize,
                        lineHeight = poemLineHeight,
                        color = fontColor,
                        lineBreak = LineBreak.Paragraph
                    ),
                    highlightText = highlightedReading,
                    highlightThickness = highlightedReadingThickness
                )
            }
        }
    }
}
