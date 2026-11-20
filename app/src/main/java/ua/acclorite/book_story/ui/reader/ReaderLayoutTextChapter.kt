/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

@Composable
fun ReaderLayoutTextChapter(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    chapter: Chapter,
    look: ReaderTextLook,
    toolbarHidden: Boolean,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    // A title may carry inline markup, footnote references included; the note
    // handler is attached here, at render time (see [withReferenceListeners]).
    val styledTitle = chapter.styledTitle
    val title = rememberRenderedLine(
        line = remember(styledTitle, chapter.title) {
            styledTitle ?: AnnotatedString(chapter.title)
        },
        searchMatches = searchMatches,
        currentSearchMatch = currentSearchMatch,
        fontColor = look.fontColor,
        openNote = openNote
    )

    // Captured from the text's layout so taps can be resolved against the
    // actual glyph positions — see [referenceTaps].
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxWidth()
    ) {
        StyledText(
            text = title,
            onTextLayout = { layoutResult = it },
            modifier = Modifier
                .padding(horizontal = look.sidePadding)
                .fillMaxWidth()
                // A reference in the title has to win over the reader's own
                // tap handling, exactly like one in a paragraph does.
                .referenceTaps(
                    text = title,
                    layout = { layoutResult },
                    uriHandler = uriHandler,
                    enabled = toolbarHidden && styledTitle != null
                ),
            style = MaterialTheme.typography.headlineMedium.let { base ->
                // The deeper the section, the smaller its title: -15% per
                // level, bottoming out at three levels deep
                val scale = 1f - 0.15f * chapter.depth.coerceAtMost(3)
                base.copy(
                    fontSize = base.fontSize * scale,
                    lineHeight = base.lineHeight * scale,
                    color = look.fontColor,
                    textAlign = look.chapterTitleAlignment.textAlignment
                )
            },
            highlightText = look.highlightedReading,
            highlightThickness = look.highlightedReadingThickness
        )
    }
}
