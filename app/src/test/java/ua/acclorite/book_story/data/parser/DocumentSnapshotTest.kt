/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ua.acclorite.book_story.data.model.file.CachedFileCompat
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.text.HtmlTextParser
import ua.acclorite.book_story.data.parser.text.MarkdownTextParser
import ua.acclorite.book_story.data.parser.text.TextParser
import ua.acclorite.book_story.data.parser.text.XmlTextParser
import ua.acclorite.book_story.domain.model.reader.ParsedText

/**
 * The whole parse of a hand-written tag-coverage book per format, pinned as a
 * snapshot — the counterpart of the EPUB one in [EpubTextParserTest]. Together
 * they are the regression net for rewriting the parsing core (#29): every
 * change in behaviour, deliberate or not, shows up as a diff of these files.
 *
 * The books are the same ones checked by eye on the tablet, so a snapshot line
 * and what the reader shows can be compared directly.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentSnapshotTest {

    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    @Test
    fun fb2TagCoverageBookMatchesItsSnapshot() {
        // The notes are part of the parse: a footnote's text is rendered by the
        // same transforms as the body
        assertSnapshot(
            "fb2/tags-test.snapshot.txt",
            parse(XmlTextParser(documentParser), "fb2/tags-test.fb2").dumpWithNotes()
        )
    }

    @Test
    fun htmlTagCoverageBookMatchesItsSnapshot() {
        assertSnapshot(
            "html/tags-test.snapshot.txt",
            parse(HtmlTextParser(documentParser), "html/tags-test.html").text.dump()
        )
    }

    @Test
    fun markdownTagCoverageBookMatchesItsSnapshot() {
        assertSnapshot(
            "md/tags-test.snapshot.txt",
            parse(
                MarkdownTextParser(MarkdownParser(CommonmarkParser.builder().build())),
                "md/tags-test.md"
            ).text.dump()
        )
    }

    private fun parse(parser: TextParser, path: String): ParsedText = runBlocking {
        parser.parse(
            CachedFileCompat.fromFile(RuntimeEnvironment.getApplication(), resourceFile(path)),
            keepImageBytes = false
        )
    }

    private fun ParsedText.dumpWithNotes(): String = buildString {
        append(text.dump())
        notes.toSortedMap().forEach { (id, note) ->
            append("Note ").append(id).append(' ').append(note.dump()).append('\n')
        }
    }
}
