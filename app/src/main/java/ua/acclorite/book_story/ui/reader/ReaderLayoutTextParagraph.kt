/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Text
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

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
fun ReaderLayoutTextParagraph(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    paragraph: Text,
    look: ReaderTextLook,
    toolbarHidden: Boolean,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    val blockIndent = BLOCK_INDENT_STEP * paragraph.role.indentSteps +
            if (paragraph.role == ReaderTextRole.TextAuthor) {
                // Authors are shifted one extra (current) character height
                with(LocalDensity.current) { look.fontSize.toDp() }
            } else 0.dp

    // Note/anchor references are parsed as listener-less clickable links;
    // the actual handler is attached here, at render time
    val line = rememberRenderedLine(
        line = paragraph.line,
        searchMatches = searchMatches,
        currentSearchMatch = currentSearchMatch,
        fontColor = look.fontColor,
        openNote = openNote
    )

    // Captured from the text's layout so taps can be resolved against the
    // actual (justify-accurate) glyph positions — see [referenceTaps].
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = look.sidePadding)
            .padding(start = blockIndent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = look.horizontalAlignment
    ) {
        StyledText(
            text = line,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.referenceTaps(
                text = line,
                layout = { layoutResult },
                uriHandler = uriHandler,
                enabled = toolbarHidden
            ),
            style = TextStyle(
                fontFamily = look.fontFamily.font,
                fontWeight = look.fontThickness.thickness,
                textAlign = look.textAlignment.textAlignment,
                textIndent = TextIndent(firstLine = look.paragraphIndentation),
                fontStyle = look.fontStyle,
                letterSpacing = look.letterSpacing,
                fontSize = look.fontSize,
                lineHeight = look.lineHeight,
                color = look.fontColor,
                lineBreak = LineBreak.Paragraph
            ),
            highlightText = look.highlightedReading,
            highlightThickness = look.highlightedReadingThickness
        )
    }
}