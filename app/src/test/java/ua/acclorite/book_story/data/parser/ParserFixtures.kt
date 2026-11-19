/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import ua.acclorite.book_story.data.parser.document.DocumentImages
import ua.acclorite.book_story.data.parser.document.DocumentParser
import ua.acclorite.book_story.domain.model.reader.ReaderText
import java.util.Base64

/*
 * Shared parser-test fixtures. The parse helpers call [DocumentParser] the way
 * the app does for each format, so when that call changes it changes here once
 * instead of in every test file.
 */

/** A 3x2 PNG, base64-encoded (an FB2 `<binary>` holds it in this form). */
const val TINY_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAEElEQVR4nGP4" +
        "z8AAQQxwFgBB0gX7h/C5SAAAAABJRU5ErkJggg=="

/** The same 3x2 PNG as bytes (an archive entry). A new array on every read. */
val TINY_PNG: ByteArray
    get() = Base64.getDecoder().decode(TINY_PNG_BASE64)

/** [body] inside an FB2 `<body>`, read as XML, as `XmlTextParser` reads it. */
fun fb2Document(body: String): Document = Jsoup.parse(
    "<FictionBook><body>$body</body></FictionBook>",
    "",
    Parser.xmlParser()
)

/** Runs an FB2 body through the parser the way `XmlTextParser` does. */
fun DocumentParser.parseFb2Body(
    body: String,
    images: DocumentImages? = null,
    keepImageBytes: Boolean = true
): List<ReaderText> = runBlocking {
    parseDocument(
        document = fb2Document(body),
        images = images,
        sectionTitles = true,
        keepImageBytes = keepImageBytes
    )
}

/**
 * Runs an HTML body through the parser the way `HtmlTextParser` does: read as
 * HTML, the first line becomes the chapter title.
 */
fun DocumentParser.parseHtmlBody(body: String): List<ReaderText> = runBlocking {
    parseDocument(
        Jsoup.parse("<html><body>$body</body></html>", "", Parser.htmlParser())
    )
}

/**
 * Runs an EPUB content document's body through the parser the way
 * `EpubTextParser` does: read as XML, `script`/`style` removed, no chapter (it
 * comes from the TOC).
 */
fun DocumentParser.parseXhtmlBody(
    body: String,
    images: DocumentImages? = null
): List<ReaderText> = runBlocking {
    val document = Jsoup.parse("<html><body>$body</body></html>", "", Parser.xmlParser()).apply {
        select("script, style").remove()
    }
    parseDocument(document = document, images = images, includeChapter = false)
}
