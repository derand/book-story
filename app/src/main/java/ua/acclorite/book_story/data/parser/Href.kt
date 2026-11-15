/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser

import java.io.ByteArrayOutputStream

/**
 * Decodes the percent escapes of an href or src from a book: `%20` → space,
 * `%C3%A9` → `é`. An href is a URL (EPUB 3.3 §5.2), while a zip entry is
 * named by the raw file name, so this is what turns one into the other.
 *
 * Unlike [java.net.URLDecoder], a form decoder, it
 * - leaves `+` alone (a `+` in a URL path is a plus, not a space);
 * - never throws: a `%` that does not start a valid escape (`pic50%.png`)
 *   stays a literal `%` — invalid by the spec, but a reader should still find
 *   the file, and one such src used to make the whole book empty;
 * - is applied once: decoding twice turns a literal `%25` into a broken `%`.
 *
 * Escaped bytes that are not valid UTF-8 come out as U+FFFD.
 */
fun decodeHref(raw: String): String {
    if ('%' !in raw) return raw

    val result = StringBuilder(raw.length)
    val bytes = ByteArrayOutputStream()

    fun flushBytes() {
        if (bytes.size() == 0) return
        result.append(bytes.toString(Charsets.UTF_8.name()))
        bytes.reset()
    }

    var i = 0
    while (i < raw.length) {
        val char = raw[i]
        val high = if (char == '%' && i + 2 < raw.length) hexValue(raw[i + 1]) else -1
        val low = if (high >= 0) hexValue(raw[i + 2]) else -1
        if (low >= 0) {
            bytes.write(high * 16 + low)
            i += 3
        } else {
            flushBytes()
            result.append(char)
            i++
        }
    }
    flushBytes()

    return result.toString()
}

private fun hexValue(char: Char): Int = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> -1
}

private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/**
 * The full zip path [href] points to, resolved against the file it appears in
 * ([from], itself a full zip path): `../Images/a.jpg` from `OEBPS/Text/c1.xhtml`
 * is `OEBPS/Images/a.jpg`. Relative URLs in an OPF, an NCX or a content document
 * resolve against that document's own location (OPF 2.0.1 §2.3, EPUB 3.3 §5.2).
 *
 * The fragment and query are dropped, the path is decoded once ([decodeHref]),
 * and `..` never climbs above the container root. A leading `/` (not allowed by
 * the spec) is read from the root.
 *
 * @return Null for a blank href, a fragment-only one, or a URL with a scheme
 *   (`http:`, `mailto:` …), which names nothing in the container.
 */
fun resolveHref(from: String, href: String): String? {
    val path = href.trim().substringBefore('#').substringBefore('?')
    if (path.isEmpty() || SCHEME.containsMatchIn(path)) return null

    val decoded = decodeHref(path)
    val segments = ArrayList<String>()
    if (!decoded.startsWith('/')) {
        segments.addAll(from.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() })
    }
    decoded.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments.add(segment)
        }
    }

    return segments.joinToString("/").ifEmpty { null }
}
