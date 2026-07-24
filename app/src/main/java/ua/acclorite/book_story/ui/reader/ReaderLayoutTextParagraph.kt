/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Text
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderFontThickness
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.FontWithName

/**
 * One step of block indentation. A paragraph's leading indent is
 * `steps(role)` of these; inside a poem block the steps are relative to the
 * poem block instead of the page — indents compose additively.
 */
internal val BLOCK_INDENT_STEP = 48.dp

/** Poem font scale (15/16) — relative, so it tracks the reader font size. */
internal const val POEM_FONT_SCALE = 0.9375f

internal val ReaderTextRole.indentSteps: Int
    get() = when (this) {
        ReaderTextRole.Paragraph -> 0
        ReaderTextRole.Title -> 1
        ReaderTextRole.Epigraph -> 2
        ReaderTextRole.TextAuthor -> 3
    }

@Composable
fun LazyItemScope.ReaderLayoutTextParagraph(
    paragraph: Text,
    showMenu: Boolean,
    fontFamily: FontWithName,
    fontColor: Color,
    lineHeight: TextUnit,
    fontThickness: ReaderFontThickness,
    fontStyle: FontStyle,
    textAlignment: ReaderTextAlignment,
    horizontalAlignment: Alignment.Horizontal,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    sidePadding: Dp,
    paragraphIndentation: TextUnit,
    doubleClickTranslation: Boolean,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    toolbarHidden: Boolean,
    openTranslator: (ReaderEvent.OnOpenTranslator) -> Unit,
    openNote: (ReaderEvent.OnOpenNote) -> Unit,
    menuVisibility: (ReaderEvent.OnMenuVisibility) -> Unit
) {
    val blockIndent = BLOCK_INDENT_STEP * paragraph.role.indentSteps +
            if (paragraph.role == ReaderTextRole.TextAuthor) {
                // Authors are shifted one extra (current) character height
                with(LocalDensity.current) { fontSize.toDp() }
            } else 0.dp

    // Note/anchor references are parsed as listener-less clickable links;
    // the actual handler is attached here, at render time
    val line = remember(paragraph.line, openNote) {
        paragraph.line.withReferenceListeners { tag ->
            openNote(ReaderEvent.OnOpenNote(tag))
        }
    }

    // Captured from the text's layout so taps can be resolved against the
    // actual (justify-accurate) glyph positions — see [dispatchLinkAt].
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .padding(start = blockIndent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = horizontalAlignment
    ) {
        StyledText(
            text = line,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.then(
                if (toolbarHidden) {
                    // A single positional handler for the whole paragraph: a
                    // tap first tries to hit a reference/link at its exact
                    // position; only if it misses does it toggle the menu.
                    // This replaces trusting Compose's per-link touch boxes,
                    // which are misaligned on justified lines.
                    Modifier.pointerInput(line, showMenu, doubleClickTranslation) {
                        // Enlarge the reference hit boxes so the small
                        // superscript footnote markers stay easy to tap.
                        val linkPadding = 12.dp.toPx()
                        detectTapGestures(
                            onDoubleTap = if (doubleClickTranslation) {
                                {
                                    openTranslator(
                                        ReaderEvent.OnOpenTranslator(
                                            textToTranslate = paragraph.line.text,
                                            translateWholeParagraph = true
                                        )
                                    )
                                }
                            } else null,
                            onTap = { position ->
                                val hitLink = layoutResult?.let { layout ->
                                    line.dispatchLinkAt(layout, position, uriHandler, linkPadding)
                                } ?: false
                                if (!hitLink) {
                                    menuVisibility(
                                        ReaderEvent.OnMenuVisibility(
                                            show = !showMenu,
                                            saveCheckpoint = true
                                        )
                                    )
                                }
                            }
                        )
                    }
                } else Modifier
            ),
            style = TextStyle(
                fontFamily = fontFamily.font,
                fontWeight = fontThickness.thickness,
                textAlign = textAlignment.textAlignment,
                textIndent = TextIndent(firstLine = paragraphIndentation),
                fontStyle = fontStyle,
                letterSpacing = letterSpacing,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = fontColor,
                lineBreak = LineBreak.Paragraph
            ),
            highlightText = highlightedReading,
            highlightThickness = highlightedReadingThickness
        )
    }
}