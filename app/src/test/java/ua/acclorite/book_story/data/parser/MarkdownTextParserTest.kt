/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.text.MarkdownTextParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * What changed when `.md` stopped going through the HTML parser: jsoup read
 * anything shaped like a tag as one, so a technical note lost `<queries>`
 * from inside a code span and whole XML snippets from its code blocks. The
 * rest of the behaviour is pinned by the Markdown snapshot in
 * [DocumentSnapshotTest].
 *
 * Still dropped: a tag-shaped run *inside* a sentence (`a <b> c`) — commonmark
 * reads it as inline HTML, which [MarkdownParser] does not render, the same as
 * for `.txt`.
 */
@RunWith(RobolectricTestRunner::class)
class MarkdownTextParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val parser = MarkdownTextParser(MarkdownParser(CommonmarkParser.builder().build()))

    @Test
    fun angleBracketsAreText() {
        val text = parse(
            """
            Title
            The manifest lists no `<queries>` element.
            <application android:allowBackup="true"/>
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "The manifest lists no <queries> element.",
                """<application android:allowBackup="true"/>"""
            ),
            text.filterIsInstance<ReaderText.Text>().map { it.line.text }
        )
    }

    @Test
    fun aByteOrderMarkIsNotPartOfTheTitle() {
        val text = parse("\uFEFFTitle\nBody.")

        assertEquals(
            "Title",
            text.filterIsInstance<ReaderText.Chapter>().single().title
        )
    }

    private fun parse(content: String): List<ReaderText> = runBlocking {
        val file = temp.newFile("book.md").apply { writeText(content) }
        parser.parse(CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), file)).text
    }
}
