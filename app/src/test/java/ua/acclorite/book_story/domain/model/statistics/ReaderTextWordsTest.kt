/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.TableAlignment

private fun text(value: String) = ReaderText.Text(line = AnnotatedString(value))

private fun anImage() = ReaderImage(
    id = "id",
    src = "src",
    bytes = ByteArray(0),
    width = 100,
    height = 100
)

class ReaderTextWordsTest {

    @Test
    fun `counts the words of a paragraph`() {
        assertEquals(4, text("one two three four").wordCount())
    }

    @Test
    fun `runs of whitespace are one gap`() {
        assertEquals(2, text("  one \n\t  two  ").wordCount())
    }

    @Test
    fun `empty text is no words`() {
        assertEquals(0, text("").wordCount())
        assertEquals(0, text("   \n ").wordCount())
    }

    @Test
    fun `a chapter counts its title`() {
        assertEquals(3, ReaderText.Chapter(title = "Part the First").wordCount())
    }

    @Test
    fun `a poem counts every line`() {
        val poem = ReaderText.Poem(
            lines = listOf(text("one two"), text("three four five"))
        )

        assertEquals(5, poem.wordCount())
    }

    @Test
    fun `a table counts every cell`() {
        val table = ReaderText.Table(
            rows = listOf(
                listOf(AnnotatedString("Name"), AnnotatedString("Year of birth")),
                listOf(AnnotatedString("Ada"), AnnotatedString("1815"))
            ),
            hasHeader = true,
            alignments = listOf(TableAlignment.Start, TableAlignment.End)
        )

        assertEquals(6, table.wordCount())
    }

    @Test
    fun `a separator is not read`() {
        assertEquals(0, ReaderText.Separator.wordCount())
    }

    @Test
    fun `a footnote counts its own words`() {
        assertEquals(5, AnnotatedString("A note on the matter").wordCount())
        assertEquals(0, AnnotatedString("").wordCount())
    }

    @Test
    fun `an image counts only its caption`() {
        val plain = ReaderText.Image(image = anImage())
        val captioned = ReaderText.Image(image = anImage(), caption = text("Figure one here"))

        assertEquals(0, plain.wordCount())
        assertEquals(3, captioned.wordCount())
    }
}
