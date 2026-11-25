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
import ua.acclorite.book_story.data.parser.text.TxtTextParser
import ua.acclorite.book_story.domain.model.reader.ReaderText

@RunWith(RobolectricTestRunner::class)
class TxtTextParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val parser = TxtTextParser(MarkdownParser(CommonmarkParser.builder().build()))

    @Test
    fun theFirstLineIsTheTitleAsTyped() {
        assertEquals("Chapter #1", title("Chapter #1\nBody."))
        assertEquals("my_book_v2", title("  my_book_v2  \nBody."))
        assertEquals("Глава (*)", title("\n\nГлава (*)\nBody."))
    }

    private fun title(content: String): String =
        parse(content).filterIsInstance<ReaderText.Chapter>().single().title

    private fun parse(content: String): List<ReaderText> = runBlocking {
        val file = temp.newFile().apply { writeText(content) }
        parser.parse(CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), file)).text
    }
}
