/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.SearchMatch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.reader.model.ReaderTextLook

/**
 * One entry of the book. What a *tap* on it means is not decided here — see
 * [readerTapNavigation]; the only gesture an entry owns is the hit test for its
 * own links, which is why [openNote] is all that is passed down.
 */
@Composable
fun ReaderLayoutText(
    entry: ReaderText,
    searchMatches: List<SearchMatch>,
    currentSearchMatch: SearchMatch?,
    look: ReaderTextLook,
    captionSpacing: Dp,
    toolbarHidden: Boolean,
    openNote: (ReaderEvent.OnOpenNote) -> Unit
) {
    when (entry) {
        is ReaderText.Image -> {
            ReaderLayoutTextImage(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                entry = entry,
                look = look,
                captionSpacing = captionSpacing,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }

        is ReaderText.Separator -> {
            ReaderLayoutTextSeparator(
                look = look,
            )
        }

        is ReaderText.Table -> {
            ReaderLayoutTextTable(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                table = entry,
                look = look,
                openNote = openNote
            )
        }

        is ReaderText.Chapter -> {
            ReaderLayoutTextChapter(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                chapter = entry,
                look = look,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }

        is ReaderText.Poem -> {
            ReaderLayoutTextPoem(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                poem = entry,
                look = look,
                openNote = openNote
            )
        }

        is ReaderText.Preformatted -> {
            ReaderLayoutTextPreformatted(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                preformatted = entry,
                look = look,
                openNote = openNote
            )
        }

        is ReaderText.Text -> {
            ReaderLayoutTextParagraph(
                searchMatches = searchMatches,
                currentSearchMatch = currentSearchMatch,
                paragraph = entry,
                look = look,
                toolbarHidden = toolbarHidden,
                openNote = openNote
            )
        }
    }
}
