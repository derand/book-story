/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText

/** The blank space that marks a chapter boundary. */
class ReaderChapterBreakTest {

    private val line = 20.dp

    private fun chapter(depth: Int = 0) =
        ReaderText.Chapter(title = "Chapter", depth = depth)

    private fun text() = ReaderText.Text(line = AnnotatedString("A paragraph."))

    private fun image() = ReaderText.Image(
        image = ReaderImage(
            id = "1",
            src = "1.jpg",
            bytes = ByteArray(0),
            width = 10,
            height = 10
        )
    )

    private fun breakAfter(
        index: Int,
        vararg entries: ReaderText,
        images: Boolean = true
    ) = chapterBreakAfter(
        text = entries.toList(),
        index = index,
        images = images,
        lineHeight = line
    )

    @Test
    fun aChapterAheadIsABreakAndOrdinaryTextIsNot() {
        assertEquals(line * 3, breakAfter(0, text(), chapter()))
        assertEquals(0.dp, breakAfter(0, text(), text()))
    }

    @Test
    fun theBookEndsWithoutABreak() {
        assertEquals(0.dp, breakAfter(1, text(), text()))
    }

    /**
     * The first chapter of a book gets no break for free: the space belongs to
     * what precedes a title, and nothing precedes this one.
     */
    @Test
    fun nothingIsDrawnBeforeTheFirstChapter() {
        val entries = arrayOf(chapter(), text(), chapter())
        assertEquals(line * 3, breakAfter(1, *entries))
        // The only break in that list is the one before the second chapter.
        assertEquals(0.dp, breakAfter(0, *entries))
    }

    /** A part title followed at once by its first chapter needs no special case. */
    @Test
    fun theBreakFallsWithTheDepthOfTheChapterThatBegins() {
        assertEquals(line * 3, breakAfter(0, text(), chapter(depth = 0)))
        assertEquals(line * 2, breakAfter(0, text(), chapter(depth = 1)))
        assertEquals(line * 1.5f, breakAfter(0, text(), chapter(depth = 2)))
        assertEquals(line * 1, breakAfter(0, text(), chapter(depth = 3)))
        assertEquals(line * 1, breakAfter(0, text(), chapter(depth = 9)))
    }

    @Test
    fun aTitleUnderAPartTitleGetsTheSmallerBreak() {
        assertEquals(line * 2, breakAfter(0, chapter(depth = 0), chapter(depth = 1)))
    }

    /**
     * With images off the entry is dropped without rendering anything, so the
     * break has to look past it or a chapter opening after an illustration
     * would lose it along with the picture.
     */
    @Test
    fun anImageThatIsNotRenderedDoesNotSwallowTheBreak() {
        assertEquals(0.dp, breakAfter(0, text(), image(), chapter(), images = true))
        assertEquals(line * 3, breakAfter(0, text(), image(), chapter(), images = false))
        assertEquals(
            line * 3,
            breakAfter(0, text(), image(), image(), chapter(), images = false)
        )
    }

    @Test
    fun aSkippedImageWithNoChapterBehindItIsStillNoBreak() {
        assertEquals(0.dp, breakAfter(0, text(), image(), text(), images = false))
        assertEquals(0.dp, breakAfter(0, text(), image(), images = false))
    }
}
