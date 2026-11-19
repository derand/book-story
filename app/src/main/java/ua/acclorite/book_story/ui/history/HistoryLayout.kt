/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.history.GroupedHistory
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.presentation.history.HistoryEvent
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.data.ScrollbarData
import ua.acclorite.book_story.ui.settings.components.SettingsSubcategoryTitle
import ua.acclorite.book_story.ui.theme.DefaultTransition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryLayout(
    listState: LazyListState,
    history: List<GroupedHistory>,
    statistics: LibraryStatistics?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onEvent: (HistoryEvent) -> Unit
) {
    DefaultTransition(visible = !isLoading) {
        LazyColumnWithScrollbar(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            scrollbarSettings = ScrollbarData.primaryScrollbar
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (statistics != null && !statistics.isEmpty) {
                item(key = "statistics") {
                    HistoryStatisticsCard(
                        statistics = statistics,
                        onClick = {
                            onEvent(HistoryEvent.OnNavigateToStatistics)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            history.forEachIndexed { index, groupedHistory ->
                item(key = groupedHistory.day.toString()) {
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    SettingsSubcategoryTitle(
                        modifier = Modifier.animateItem(),
                        title = dayTitle(groupedHistory.day),
                        padding = 16.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(
                    groupedHistory.history,
                    key = { "${it.day}/${it.book.id}" }
                ) { historyEntry ->
                    HistoryItem(
                        historyEntry = historyEntry,
                        isRefreshing = isRefreshing,
                        onBodyClick = {
                            onEvent(
                                HistoryEvent.OnNavigateToBookInfo(
                                    historyEntry.book.id
                                )
                            )
                        },
                        onTitleClick = {
                            onEvent(
                                HistoryEvent.OnNavigateToReader(
                                    historyEntry.book.id
                                )
                            )
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
/** "Today", "Yesterday", or the date in the app locale's medium format. */
@Composable
private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    val locale = LocalConfiguration.current.locales[0]
    return when (day) {
        today -> stringResource(id = R.string.today)
        today.minusDays(1) -> stringResource(id = R.string.yesterday)
        else -> remember(day, locale) {
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
}
