/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.reader

import androidx.compose.foundation.lazy.LazyListScope
import ua.acclorite.book_story.ui.settings.reader.chapters.ChaptersSubcategory
import ua.acclorite.book_story.ui.settings.reader.font.FontSubcategory
import ua.acclorite.book_story.ui.settings.reader.images.ImagesSubcategory
import ua.acclorite.book_story.ui.settings.reader.misc.MiscSubcategory
import ua.acclorite.book_story.ui.settings.reader.padding.PaddingSubcategory
import ua.acclorite.book_story.ui.settings.reader.progress.ProgressSubcategory
import ua.acclorite.book_story.ui.settings.reader.reading_mode.ReadingModeSubcategory
import ua.acclorite.book_story.ui.settings.reader.reading_speed.ReadingSpeedSubcategory
import ua.acclorite.book_story.ui.settings.reader.system.SystemSubcategory
import ua.acclorite.book_story.ui.settings.reader.text.TextSubcategory
import ua.acclorite.book_story.ui.settings.reader.translator.TranslatorSubcategory

fun LazyListScope.ReaderSettingsCategory() {
    FontSubcategory()
    TextSubcategory()
    ImagesSubcategory()
    ChaptersSubcategory()
    ReadingModeSubcategory()
    PaddingSubcategory()
    SystemSubcategory()
    ReadingSpeedSubcategory()
    ProgressSubcategory()
    TranslatorSubcategory()
    MiscSubcategory(
        showDivider = false
    )
}