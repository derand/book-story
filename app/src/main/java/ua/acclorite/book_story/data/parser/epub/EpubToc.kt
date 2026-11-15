/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.parser.resolveHref
import ua.acclorite.book_story.domain.model.reader.ReaderText

private const val TAG = "EpubToc"

/**
 * Chapter titles from the book's table of contents, keyed by the lowercased
 * full zip path of the document each one points to ([tocKey]).
 *
 * The EPUB 3 navigation document (`<nav epub:type="toc">`) when the book has
 * one, else the EPUB 2 NCX — EPUB 3 keeps the NCX only for older readers.
 * When several entries point into one document, the first names it: the
 * others are headings inside it (`#anchors`), and a single-file book — a
 * Google Docs export — would otherwise get every heading of the book joined
 * into one title. An entry nested under one for another document is one level
 * deep.
 *
 * @return Null when the book has no TOC at all.
 */
fun EpubPackage.readToc(): Map<String, ReaderText.Chapter>? {
    val points = navDocument?.let { nav ->
        navPoints(nav.name, read(nav)).takeIf { it.isNotEmpty() }
            ?.also { logI(TAG, "TOC from the navigation document [${nav.name}].") }
    } ?: ncx?.let { ncx ->
        ncxPoints(ncx.name, read(ncx))
            .also { logI(TAG, "TOC from the NCX [${ncx.name}].") }
    } ?: return null

    val titles = mutableMapOf<String, ReaderText.Chapter>()
    points.forEach { point ->
        // The document as the spine will name it, if the href is a little off
        val key = tocKey(entry(point.path)?.name ?: point.path)
        titles.getOrPut(key) {
            ReaderText.Chapter(title = point.title, depth = if (point.nested) 1 else 0)
        }
    }
    return titles
}

/** The key [readToc] files the title of the document at [path] under. */
fun tocKey(path: String): String = path.lowercase()

/**
 * One TOC entry: its [title], the full zip [path] of the document it points
 * to, and whether it sits under an entry for another document.
 */
private class TocPoint(val title: String, val path: String, val nested: Boolean)

/** The `navPoint`s of an NCX, in document order. */
private fun ncxPoints(ncxPath: String, content: String): List<TocPoint> {
    // XML, not HTML: read as HTML, <content src="…"/> is left open and a
    // nested navPoint ends up inside it rather than inside its parent
    val document = Jsoup.parse(content, "", Parser.xmlParser())

    fun Element.path(): String? =
        selectFirst("> content")?.attr("src")?.let { resolveHref(ncxPath, it) }

    return document.select("navPoint").mapNotNull { navPoint ->
        val title = navPoint.selectFirst("> navLabel > text")?.text()?.trim()
        val path = navPoint.path()
        if (title.isNullOrEmpty() || path == null) return@mapNotNull null

        val parentPath = navPoint.parent()
            ?.takeIf { it.tagName().equals("navPoint", ignoreCase = true) }
            ?.path()
        TocPoint(title, path, nested = parentPath != null && parentPath != path)
    }
}

/**
 * The entries of the `toc` nav of an EPUB 3 navigation document, in document
 * order: `nav > ol > li > a`, nested by `li > ol`. A `span` heading without a
 * link gives no entry, and its children count as top level.
 */
private fun navPoints(navPath: String, content: String): List<TocPoint> {
    val document = Jsoup.parse(content, "", Parser.xmlParser())
    val navs = document.select("nav")
    val toc = navs.firstOrNull { nav ->
        nav.attr("epub:type").split(Regex("\\s+")).contains("toc")
    } ?: navs.firstOrNull() ?: return emptyList()

    val points = mutableListOf<TocPoint>()
    fun walk(list: Element, parentPath: String?) {
        list.children().filter { it.tagName().equals("li", ignoreCase = true) }.forEach { item ->
            val label = item.children().firstOrNull { child ->
                child.tagName().equals("a", ignoreCase = true) ||
                        child.tagName().equals("span", ignoreCase = true)
            }
            val title = label?.text()?.trim()
            val path = label
                ?.takeIf { it.tagName().equals("a", ignoreCase = true) }
                ?.attr("href")
                ?.let { resolveHref(navPath, it) }
            if (!title.isNullOrEmpty() && path != null) {
                points.add(TocPoint(title, path, nested = parentPath != null && parentPath != path))
            }

            item.children()
                .firstOrNull { it.tagName().equals("ol", ignoreCase = true) }
                ?.let { walk(it, path) }
        }
    }
    toc.children().filter { it.tagName().equals("ol", ignoreCase = true) }.forEach { walk(it, null) }
    return points
}
