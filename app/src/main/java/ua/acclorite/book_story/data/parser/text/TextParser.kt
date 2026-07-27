/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.text

import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.reader.ParsedText

interface TextParser {
    /**
     * @param keepImageBytes When false, images keep their size (so the layout
     *   still reserves their slot and the parse stays cacheable) but drop their
     *   encoded bytes, which the reader is not going to show anyway.
     */
    suspend fun parse(cachedFile: CachedFile, keepImageBytes: Boolean = true): ParsedText
}