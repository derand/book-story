/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.log.messageForLog
import ua.acclorite.book_story.domain.model.history.History
import ua.acclorite.book_story.domain.model.library.bookSearchTokens
import ua.acclorite.book_story.domain.model.library.matchesSearch
import ua.acclorite.book_story.domain.repository.HistoryRepository
import ua.acclorite.book_story.presentation.history.model.GroupedHistory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val TAG = "ObserveHistory"

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {

    /**
     * The history narrowed to [query] (blank: every entry) and grouped by day,
     * again after each change to the history, its books or the query. A failed
     * read leaves an empty list.
     */
    operator fun invoke(query: Flow<String>): Flow<List<GroupedHistory>> =
        combine(
            historyRepository.observeHistory(),
            query.map { bookSearchTokens(it) }
        ) { entries, tokens ->
            entries
                .filter { history -> history.book.matchesSearch(tokens) }
                .sortedByDescending { history -> history.time }
                .groupBy { history -> getDayLabel(history.time) }
                .map { (day, history) -> GroupedHistory(day, filterMaxElementsById(history)) }
                .also { logI(TAG, "Got ${it.size} grouped history entries.") }
        }.catch {
            logE(TAG, "Could not get grouped history entries with error: ${it.messageForLog()}")
            emit(emptyList())
        }

    private fun getDayLabel(timeMillis: Long): String {
        val historyDate = Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        return when (historyDate) {
            today -> "today"
            yesterday -> "yesterday"
            else -> historyDate.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
        }
    }

    private fun filterMaxElementsById(elements: List<History>): List<History> {
        val groupedById = elements.groupBy { it.book.id }
        val maxElementsById = groupedById.map { (_, values) ->
            values.maxByOrNull { it.time }
        }
        return maxElementsById.filterNotNull()
    }
}
