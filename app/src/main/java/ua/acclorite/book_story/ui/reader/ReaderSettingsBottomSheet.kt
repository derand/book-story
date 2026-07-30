/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import ua.acclorite.book_story.presentation.reader.ReaderEvent
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.modal_bottom_sheet.ModalBottomSheet
import ua.acclorite.book_story.ui.settings.appearance.colors.ColorsSubcategory
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

private var initialPage = 0

/**
 * Share of the screen the sheet takes. The rest of it is the page being read —
 * kept the same on every tab, because a sheet that resizes as you move between
 * them is a jump in the one place the eye is trying to compare a before and an
 * after.
 */
private const val HEIGHT_FRACTION = 0.65f

/**
 * The reader's settings, over the page they change.
 *
 * The scrim is transparent and the reader's own bars are hidden while this is
 * open (see [ReaderScaffold]), so the strip above the sheet shows the book as the
 * settings being touched will leave it — not only for colors, but for margins,
 * font and image width just as much.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    dismissBottomSheet: (ReaderEvent.OnDismissBottomSheet) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage) { 3 }
    DisposableEffect(Unit) { onDispose { initialPage = pagerState.currentPage } }

    ModalBottomSheet(
        hasFixedHeight = true,
        scrimColor = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(HEIGHT_FRACTION),
        dragHandle = {},
        onDismissRequest = {
            dismissBottomSheet(ReaderEvent.OnDismissBottomSheet)
        },
        sheetGesturesEnabled = false
    ) {
        ReaderSettingsBottomSheetTabRow(
            currentPage = pagerState.currentPage,
            scrollToPage = {
                scope.launch {
                    pagerState.animateScrollToPage(it)
                }
            }
        )

        HorizontalPager(state = pagerState) { page ->
            when (page) {
                0 -> {
                    LazyColumnWithScrollbar(Modifier.fillMaxSize()) {
                        ReadingModeSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        PaddingSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        SystemSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        ReadingSpeedSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        MiscSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface },
                            showDivider = false
                        )
                    }
                }

                1 -> {
                    LazyColumnWithScrollbar(Modifier.fillMaxSize()) {
                        FontSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        TextSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        ImagesSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        ChaptersSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        ProgressSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface }
                        )
                        TranslatorSubcategory(
                            titleColor = { MaterialTheme.colorScheme.onSurface },
                            showDivider = false
                        )
                    }
                }

                2 -> {
                    LazyColumnWithScrollbar(Modifier.fillMaxSize()) {
                        ColorsSubcategory(
                            showTitle = false,
                            showDivider = false,
                            backgroundColor = { MaterialTheme.colorScheme.surfaceContainer }
                        )
                    }
                }
            }
        }
    }
}
