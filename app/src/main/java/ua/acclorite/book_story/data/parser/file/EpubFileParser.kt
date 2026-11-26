/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.data.parser.epub.EpubPackage
import ua.acclorite.book_story.domain.model.library.Book
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "EpubFileParser"

class EpubFileParser @Inject constructor() : FileParser {

    override suspend fun parse(cachedFile: CachedFile): Book? {
        return try {
            var book: Book? = null

            val rawFile = cachedFile.rawFile
            if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return null

            withContext(Dispatchers.IO) {
                ZipFile(rawFile).use { zip ->
                    val document = EpubPackage.open(zip).opf ?: return@withContext

                    // The first title is the book's; the others are subtitles,
                    // series or collection names
                    val title = document.select("metadata > dc|title")
                        .map { it.text().trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?: cachedFile.name.substringBeforeLast(".").trim()

                    val author = authors(document).joinToString(", ").run {
                        if (isBlank()) {
                            UIText.StringResource(R.string.unknown_author)
                        } else {
                            UIText.StringValue(this)
                        }
                    }

                    val description = Jsoup.parse(
                        document.select("metadata > dc|description").text()
                    ).text().run {
                        ifBlank {
                            null
                        }
                    }

                    book = Book(
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
                }
            }
            book
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logE(TAG, "Could not parse file with message: ${e.messageForLog()}.")
            null
        }
    }
}

/**
 * The creators who wrote the book: those without a role or with `aut`. An
 * illustrator, translator or editor is a creator too. The role is an
 * `opf:role` attribute (EPUB 2) or a `role` meta refining the creator's id
 * (EPUB 3).
 */
private fun authors(opf: Document): List<String> {
    val refinedRoles = opf.select("metadata > meta[property=role][refines]")
        .associate { it.attr("refines").removePrefix("#") to it.text().trim() }

    return opf.select("metadata > dc|creator")
        .filter { creator ->
            val role = creator.attr("opf:role").ifBlank {
                refinedRoles[creator.id()].orEmpty()
            }
            role.isBlank() || role.equals("aut", ignoreCase = true)
        }
        .map { it.text().trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}
