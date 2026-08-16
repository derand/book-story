/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the reader's search actually finds: where a match sits, which string of
 * an item carries it, and the two rules footnotes brought with them — a note is
 * reached through its references, and a note nothing refers to is not reached
 * at all.
 */
class BookSearchTest {

    private fun paragraph(text: String) = ReaderText.Text(AnnotatedString(text))

    /** A paragraph carrying a footnote reference rendered as [marker]. */
    private fun paragraphWithNote(
        before: String,
        marker: String,
        noteId: String,
        after: String = ""
    ) = ReaderText.Text(
        buildAnnotatedString {
            append(before)
            withLink(
                LinkAnnotation.Clickable(
                    tag = "$NOTE_LINK_TAG_PREFIX$noteId",
                    linkInteractionListener = null
                )
            ) {
                append(marker)
            }
            append(after)
        }
    )

    @Test
    fun `finds every occurrence in reading order`() = runBlocking {
        val text = listOf(
            paragraph("The lamp was lit."),
            paragraph("No lamp here, and no lamp there.")
        )

        val matches = text.findSearchMatches("lamp")

        assertEquals(3, matches.size)
        assertEquals(listOf(0, 1, 1), matches.map { it.itemIndex })
        assertEquals(4, matches[0].start)
        assertEquals(8, matches[0].end)
        // Both occurrences of the second paragraph, in the order they read.
        assertEquals(listOf(3, 21), matches.drop(1).map { it.start })
    }

    @Test
    fun `matching ignores case`() = runBlocking {
        val matches = listOf(paragraph("Lamp, lamp, LAMP")).findSearchMatches("lAmP")
        assertEquals(3, matches.size)
    }

    @Test
    fun `a query shorter than the minimum is not searched`() = runBlocking {
        val text = listOf(paragraph("a lamp"))
        assertTrue(text.findSearchMatches("a").isEmpty())
        assertTrue(text.findSearchMatches(" ").isEmpty())
        assertEquals(1, text.findSearchMatches("la").size)
    }

    @Test
    fun `chapter titles are searched, styled or plain`() = runBlocking {
        val text = listOf(
            ReaderText.Chapter(title = "The Lamp"),
            ReaderText.Chapter(
                title = "Another Lamp",
                styledTitle = AnnotatedString("Another Lamp")
            )
        )

        val matches = text.findSearchMatches("lamp")

        assertEquals(listOf(0, 1), matches.map { it.itemIndex })
        assertEquals(listOf(4, 8), matches.map { it.start })
    }

    @Test
    fun `a poem line is identified by its part`() = runBlocking {
        val poem = ReaderText.Poem(
            lines = listOf(
                ReaderText.Text(AnnotatedString("a lamp")),
                ReaderText.Text(AnnotatedString("no light")),
                ReaderText.Text(AnnotatedString("the lamp again"))
            )
        )

        val matches = listOf(poem).findSearchMatches("lamp")

        assertEquals(listOf(0, 2), matches.map { it.part })
    }

    @Test
    fun `a table cell is numbered by its place in the drawn grid`() = runBlocking {
        // A ragged table: the second row is short, and the renderer still draws
        // three columns — so the third row's first cell is part 6, not part 5.
        val table = ReaderText.Table(
            rows = listOf(
                listOf(AnnotatedString("a"), AnnotatedString("b"), AnnotatedString("c")),
                listOf(AnnotatedString("lamp")),
                listOf(AnnotatedString("lamp"), AnnotatedString("x"), AnnotatedString("y"))
            ),
            hasHeader = true
        )

        val matches = listOf(table).findSearchMatches("lamp")

        assertEquals(listOf(3, 6), matches.map { it.part })
    }

    @Test
    fun `an image caption is searched`() = runBlocking {
        val image = ReaderText.Image(
            image = ReaderImage(
                id = "cover",
                src = "cover.jpg",
                bytes = ByteArray(0),
                width = 100,
                height = 100
            ),
            caption = ReaderText.Text(AnnotatedString("The lamp, lit"))
        )

        val matches = listOf(image).findSearchMatches("lamp")

        assertEquals(1, matches.size)
        assertEquals(4, matches[0].start)
    }

    @Test
    fun `a footnote is found through its reference in the text`() = runBlocking {
        val text = listOf(paragraphWithNote(before = "Nothing here", marker = "[1]", noteId = "n1"))
        val notes = mapOf("n1" to AnnotatedString("The lamp was a gift."))

        val matches = text.findSearchMatches("lamp", notes)

        assertEquals(1, matches.size)
        with(matches.single()) {
            assertTrue(inNote)
            // The reference, which is the only part of a note that is on the page.
            assertEquals(12, start)
            assertEquals(15, end)
        }
    }

    @Test
    fun `a footnote referred to twice is two matches`() = runBlocking {
        val text = listOf(
            paragraphWithNote(before = "First", marker = "[1]", noteId = "n1"),
            paragraph("Between"),
            paragraphWithNote(before = "Second", marker = "[1]", noteId = "n1")
        )
        val notes = mapOf("n1" to AnnotatedString("The lamp."))

        val matches = text.findSearchMatches("lamp", notes)

        assertEquals(listOf(0, 2), matches.map { it.itemIndex })
        assertTrue(matches.all { it.inNote })
    }

    @Test
    fun `a footnote nothing refers to is not counted`() = runBlocking {
        val text = listOf(paragraph("Nothing here"))
        val notes = mapOf("orphan" to AnnotatedString("The lamp."))

        assertTrue(text.findSearchMatches("lamp", notes).isEmpty())
    }

    @Test
    fun `a note anchor does not double up with the text under it`() = runBlocking {
        // The reference's own display text matches too: one stop on the page,
        // not two.
        val text = listOf(paragraphWithNote(before = "See ", marker = "lamp", noteId = "n1"))
        val notes = mapOf("n1" to AnnotatedString("The lamp again."))

        val matches = text.findSearchMatches("lamp", notes)

        assertEquals(1, matches.size)
        assertTrue("the visible text wins over the note anchor", !matches.single().inNote)
    }

    @Test
    fun `matches inside one item are ordered by part and offset`() = runBlocking {
        val poem = ReaderText.Poem(
            lines = listOf(
                ReaderText.Text(AnnotatedString("lamp and lamp")),
                ReaderText.Text(AnnotatedString("lamp"))
            )
        )

        val matches = listOf(poem).findSearchMatches("lamp")

        assertEquals(listOf(0 to 0, 0 to 9, 1 to 0), matches.map { it.part to it.start })
    }
}
