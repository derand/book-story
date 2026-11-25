/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.file

import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.library.Book
import javax.inject.Inject

class TxtFileParser @Inject constructor() : FileParser {

    override suspend fun parse(cachedFile: CachedFile): Book = Book(
        title = cachedFile.name.substringBeforeLast(".").trim(),
        author = UIText.StringResource(R.string.unknown_author),
        description = null,
        scrollIndex = 0,
        scrollOffset = 0,
        progress = 0f,
        filePath = cachedFile.path,
        lastOpened = null,
        categories = emptyList(),
        coverImage = null
    )
}
