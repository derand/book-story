/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser as CommonmarkParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.data.parser.document.MarkdownParser
import ua.acclorite.book_story.data.parser.text.markChapterTitles
import ua.acclorite.book_story.domain.model.reader.ReaderText

/**
 * Punctuation the author typed reaches the reader untouched. The parser used
 * to encode styling as literal "**"/"_" and then strip every "*"/"_" left in
 * the text, which deleted the book's own asterisks and underscores along with
 * its own leftovers — "(*)" came out as "()".
 */
@RunWith(RobolectricTestRunner::class)
class LiteralMarkdownCharsTest {

    // The default builder enables every block type, while the app narrows them
    // (see AppModule): a stricter parser than production, on purpose.
    private val documentParser = DocumentParser(
        MarkdownParser(CommonmarkParser.builder().build())
    )

    @Test
    fun loneAsteriskSurvives() {
        // The footnote convention of the book that surfaced this
        assertEquals(
            "(*): виноска до попереднього абзацу.",
            text("<p>(*): виноска до попереднього абзацу.</p>").single()
        )
    }

    @Test
    fun numberedAsteriskReferenceSurvives() {
        assertEquals(
            "Після події (*1), що сталася торік, минув місяць.",
            text("<p>Після події (*1), що сталася торік, минув місяць.</p>").single()
        )
    }

    @Test
    fun underscoresInAUrlSurvive() {
        val url = "https://example.org/wiki/Some_Long_Title_With_Underscores"
        assertEquals("Переклад узято з $url", text("<p>Переклад узято з $url</p>").single())
    }

    @Test
    fun underscoresInACyrillicUrlSurvive() {
        val url = "https://example.org/wiki/Some_Long_Title_With_Underscores/" +
                "Музична_рубрика#Вступ"
        assertEquals("Читайте на $url.", text("<p>Читайте на $url.</p>").single())
    }

    @Test
    fun twoAsterisksOnOneLineSurvive() {
        assertEquals(
            "Виноски (*1) і (*2) в одному абзаці.",
            text("<p>Виноски (*1) і (*2) в одному абзаці.</p>").single()
        )
    }

    @Test
    fun twoNumberedReferencesDoNotPairIntoEmphasis() {
        // The shape a footnoted book uses twice in one paragraph: a closing
        // "*" followed by a digit is not right-flanking, so CommonMark leaves
        // both alone
        val paragraph = paragraphs(
            "<p>Названа (*1) служба. (*1): розшифрування.</p>"
        ).single()

        assertEquals("Названа (*1) служба. (*1): розшифрування.", paragraph.line.text)
        assertTrue("nothing was emphasised", paragraph.line.spanStyles.isEmpty())
    }

    @Test
    fun asterisksSurviveNextToAnEmphasisRun() {
        val paragraph = paragraphs(
            "<p>Дивись (*) і <emphasis>ось це</emphasis> теж.</p>"
        ).single()

        assertEquals("Дивись (*) і ось це теж.", paragraph.line.text)
        assertTrue(
            "the emphasis still styles only its own run",
            paragraph.line.spanStyles.any { span ->
                span.item.fontStyle == FontStyle.Italic &&
                        paragraph.line.text.substring(span.start, span.end) == "ось це"
            }
        )
    }

    @Test
    fun boldIsStyledWithoutLeakingAsterisks() {
        val paragraph = paragraphs("<p>Це <strong>жирний</strong> текст.</p>").single()

        assertEquals("Це жирний текст.", paragraph.line.text)
        assertTrue(
            "bold span",
            paragraph.line.spanStyles.any { span ->
                span.item.fontWeight == FontWeight.Medium &&
                        paragraph.line.text.substring(span.start, span.end) == "жирний"
            }
        )
    }

    @Test
    fun boldSurvivesAnAdjacentLiteralAsterisk() {
        // The old "**" injection sat right against the author's asterisk, and
        // the cleanup then swallowed the author's one along with its own
        val paragraph = paragraphs("<p>(*)<strong>жирний</strong> текст.</p>").single()

        assertEquals("(*)жирний текст.", paragraph.line.text)
        assertTrue(
            "bold span",
            paragraph.line.spanStyles.any { span ->
                span.item.fontWeight == FontWeight.Medium &&
                        paragraph.line.text.substring(span.start, span.end) == "жирний"
            }
        )
    }

    @Test
    fun paddedEmphasisTagKeepsItsSpaces() {
        // "** жирний **" was never valid markdown, hence the normalize regexes;
        // a sentinel is a toggle, so the padding is simply part of the run
        val paragraph = paragraphs("<p>Це <strong> жирний </strong>текст.</p>").single()

        assertEquals("Це  жирний текст.", paragraph.line.text)
    }

    @Test
    fun sentinelsCarriedByTheBookAreDropped() {
        // A book of its own may use the private-use area (Apple's logo, legacy
        // CJK fonts): those characters must not toggle our styles
        val paragraph = paragraphs(
            "<p>Текст\uE018з\uE019приватної\uE011зони.</p>"
        ).single()

        assertEquals("Текстзприватноїзони.", paragraph.line.text)
        assertTrue("no styling leaked", paragraph.line.spanStyles.isEmpty())
    }

    @Test
    fun knownLimit_authorsValidMarkdownIsStillConsumed() {
        // Two bare "(*)" in one paragraph are a valid emphasis pair by
        // CommonMark's flanking rules, so the asterisks are eaten and the
        // text between them turns italic. Inherent to running the markdown
        // parser over prose at all — see #29, which removes that step.
        val paragraph = paragraphs("<p>Дивись (*) і теж (*).</p>").single()

        assertEquals("Дивись () і теж ().", paragraph.line.text)
    }

    @Test
    fun sceneBreakIsStillKeptVisible() {
        // The separator branch predates this fix; make sure it still holds
        assertEquals("* * *", text("<p>* * *</p>").single())
    }

    @Test
    fun asterisksInAChapterTitleSurvive() {
        val chapter = parse("<section><title><p>Глава (*) перша</p></title><p>Текст.</p></section>")
            .filterIsInstance<ReaderText.Chapter>()
            .single()

        assertEquals("Глава (*) перша", chapter.title)
    }

    // --- helpers ---

    private fun text(body: String): List<String> =
        paragraphs(body).map { paragraph -> paragraph.line.text }

    private fun paragraphs(body: String): List<ReaderText.Text> =
        // A <title> keeps the body text from being taken as the chapter title
        parse("<section><title><p>Розділ</p></title>$body</section>")
            .filterIsInstance<ReaderText.Text>()

    /** Runs the FB2 body through the same two steps as [XmlTextParser]. */
    private fun parse(body: String): List<ReaderText> = runBlocking {
        val document = Jsoup.parse(
            """<FictionBook><body>$body</body></FictionBook>""",
            "",
            Parser.xmlParser()
        )
        document.markChapterTitles()
        documentParser.parseDocument(document)
    }
}
