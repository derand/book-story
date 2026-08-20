/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.presentation.reader.model.ReaderTextAlignment
import ua.acclorite.book_story.ui.common.components.common.StyledText

@Composable
fun LazyItemScope.ReaderLayoutTextChapter(
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    chapter: Chapter,
    chapterTitleAlignment: ReaderTextAlignment,
    fontColor: Color,
    sidePadding: Dp,
    highlightedReading: Boolean,
    highlightedReadingThickness: FontWeight,
    toolbarHidden: Boolean,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    // A title may carry inline markup, footnote references included; the note
    // handler is attached here, at render time (see [withReferenceListeners]).
    val styledTitle = chapter.styledTitle
    val title = remember(
        styledTitle, chapter.title, openNote, searchMatches, currentSearchMatch, fontColor
    ) {
        val rendered = styledTitle?.withReferenceListeners { tag ->
            openNote(ReaderEvent.OnOpenNote(tag))
        } ?: AnnotatedString(chapter.title)

        rendered.withSearchHighlights(
            matches = searchMatches,
            current = currentSearchMatch,
            fontColor = fontColor
        )
    }

    // Captured from the text's layout so taps can be resolved against the
    // actual glyph positions — see [dispatchLinkAt].
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .animateItem(
                fadeInSpec = null,
                fadeOutSpec = null
            )
            .fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(22.dp))

        StyledText(
            text = title,
            onTextLayout = { layoutResult = it },
            modifier = Modifier
                .padding(horizontal = sidePadding)
                .fillMaxWidth()
                .then(
                    if (toolbarHidden && styledTitle != null) {
                        // A reference in the title has to win over the reader's
                        // own tap handling, exactly like one in a paragraph does.
                        Modifier.pointerInput(title) {
                            val linkPadding = 12.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val layout = layoutResult ?: return@awaitEachGesture
                                if (title.linkAt(layout, down.position, linkPadding) == null) {
                                    return@awaitEachGesture
                                }

                                down.consume()
                                // See the paragraph's twin: a press that
                                // travelled is not a tap on a link.
                                val up = awaitPress(down)
                                if (up !is PressOutcome.Tap) return@awaitEachGesture
                                up.change.consume()
                                title.linkAt(layout, up.change.position, linkPadding)
                                    ?.dispatch(uriHandler)
                            }
                        }
                    } else Modifier
                ),
            style = MaterialTheme.typography.headlineMedium.let { base ->
                // The deeper the section, the smaller its title: -15% per
                // level, bottoming out at three levels deep
                val scale = 1f - 0.15f * chapter.depth.coerceAtMost(3)
                base.copy(
                    fontSize = base.fontSize * scale,
                    lineHeight = base.lineHeight * scale,
                    color = fontColor,
                    textAlign = chapterTitleAlignment.textAlignment
                )
            },
            highlightText = highlightedReading,
            highlightThickness = highlightedReadingThickness
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = fontColor.copy(0.4f))
        Spacer(modifier = Modifier.height(16.dp))
    }
}
