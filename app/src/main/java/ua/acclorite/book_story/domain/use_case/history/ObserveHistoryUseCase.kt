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
import ua.acclorite.book_story.domain.model.history.GroupedHistory
import ua.acclorite.book_story.domain.model.history.groupHistory
import ua.acclorite.book_story.domain.model.library.bookSearchTokens
import ua.acclorite.book_story.domain.model.library.matchesSearch
import ua.acclorite.book_story.domain.repository.HistoryRepository
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "ObserveHistory"

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {

    /**
     * The reading sessions narrowed to [query] (blank: every book) and grouped
     * by day and book, again after each change to the sessions, their books or
     * the query. A failed read is the last value, a failure: nothing follows
     * until the next call.
     */
    operator fun invoke(query: Flow<String>): Flow<Result<List<GroupedHistory>>> =
        combine(
            historyRepository.observeHistory(),
            query.map { bookSearchTokens(it) }
        ) { sessions, tokens ->
            groupHistory(
                sessions = sessions.filter { it.book.matchesSearch(tokens) },
                zone = ZoneId.systemDefault()
            )
                .also { logI(TAG, "Got ${it.size} days of history.") }
                .let { Result.success(it) }
        }.catch {
            logE(TAG, "Could not get the history with error: ${it.messageForLog()}")
            emit(Result.failure(it))
        }
}
