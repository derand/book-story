/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.file

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.library.Book
import javax.inject.Inject

private const val TAG = "Fb2FileParser"

class Fb2FileParser @Inject constructor() : FileParser {

    override suspend fun parse(cachedFile: CachedFile): Book? {
        return try {
            val document = cachedFile.openInputStream()?.use {
                Jsoup.parse(it, null, "", Parser.xmlParser())
            }

            // Only the book's own metadata: `document-info` names whoever made
            // the file, `src-title-info` the original, and the body has
            // annotations of its own sections
            val titleInfo = document?.selectFirst("description > title-info")

            val title = titleInfo?.selectFirst("> book-title")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run cachedFile.name.substringBeforeLast(".").trim()
                }
                this
            }

            val author = titleInfo?.select("> author").orEmpty()
                .mapNotNull { authorName(it) }
                .distinct()
                .joinToString(", ")
                .run {
                    if (isBlank()) {
                        return@run UIText.StringResource(R.string.unknown_author)
                    }
                    UIText.StringValue(this)
                }

            val description = titleInfo?.selectFirst("> annotation")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run null
                }
                this
            }

            Book(
                title = title,
                author = author,
                description = description,
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = cachedFile.path,
                lastOpened = null,
                categories = emptyList(),
                coverImage = null
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse file with message: ${e.messageForLog()}.")
            null
        }
    }
}

/**
 * One `<author>` as a name: the name parts joined by a space (they are often
 * written with no whitespace between the tags), else the nickname. Everything
 * else in it — `<id>`, `<email>`, `<home-page>` — is not part of the name.
 */
private fun authorName(author: Element): String? {
    fun part(tag: String) = author.selectFirst("> $tag")?.text()?.trim()?.takeIf { it.isNotEmpty() }

    val name = listOf("first-name", "middle-name", "last-name")
        .mapNotNull { part(it) }
        .joinToString(" ")
    return name.ifEmpty { part("nickname") }
}
