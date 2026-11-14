/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.core.helpers.ListSearch
import ua.acclorite.book_story.core.helpers.launchWhileSubscribed
import ua.acclorite.book_story.domain.model.history.History
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import ua.acclorite.book_story.domain.use_case.history.AddHistoryUseCase
import ua.acclorite.book_story.domain.use_case.history.DeleteHistoryUseCase
import ua.acclorite.book_story.domain.use_case.history.DeleteWholeHistoryUseCase
import ua.acclorite.book_story.domain.use_case.history.ObserveHistoryUseCase
import ua.acclorite.book_story.domain.use_case.statistics.ObserveLibraryStatisticsUseCase
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HistoryModel @Inject constructor(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val addHistoryUseCase: AddHistoryUseCase,
    private val observeLibraryStatisticsUseCase: ObserveLibraryStatisticsUseCase,
    private val deleteHistoryUseCase: DeleteHistoryUseCase,
    private val deleteWholeHistoryUseCase: DeleteWholeHistoryUseCase,
    private val getBookUseCase: GetBookUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<HistoryEffect>()
    val effects = _effects.asSharedFlow()

    private val search = ListSearch(viewModelScope)

    /** Bumped by pull-to-refresh, which reads the list again from scratch. */
    private val reload = MutableStateFlow(0)

    init {
        // The list and the statistics card follow the database, so nothing has
        // to say "refresh" after a write; but only while the screen shows them:
        // this model lives as long as the activity, and the reader writes on
        // every settled scroll.
        viewModelScope.launchWhileSubscribed(_state) {
            reload.collectLatest {
                combine(
                    observeHistoryUseCase(search.filter),
                    observeLibraryStatisticsUseCase()
                ) { history, statistics -> history to statistics }
                    .flowOn(Dispatchers.Default)
                    .collect { (history, statistics) ->
                        _state.update {
                            it.copy(
                                history = history.getOrDefault(emptyList()),
                                loadFailed = history.isFailure,
                                statistics = statistics,
                                isLoading = false
                            )
                        }
                    }
            }
        }

        viewModelScope.launch {
            HistoryScreen.insertHistoryChannel.receiveAsFlow().collectLatest { bookId ->
                getBookUseCase(bookId)?.let { book ->
                    addHistoryUseCase(
                        History(
                            id = 0,
                            book = book,
                            time = Date().time
                        )
                    )
                }
            }
        }
    }

    private fun send(effect: HistoryEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private var refreshJob: Job? = null

    /**
     * Handled in the order sent: state changes at once, and only the work
     * that waits (the database, an effect) is launched.
     */
    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.OnRefreshList -> {
                _state.update {
                    it.copy(
                        isRefreshing = true,
                        isLoading = event.loading,
                        showSearch = if (event.hideSearch) false else it.showSearch
                    )
                }
                if (event.hideSearch) search.clear()
                reload.value++

                refreshJob?.cancel()
                refreshJob = viewModelScope.launch {
                    delay(500) // Long enough for the indicator to be seen
                    _state.update { it.copy(isRefreshing = false) }
                }
            }

            is HistoryEvent.OnSearchVisibility -> {
                if (!event.show) search.clear()
                _state.update {
                    it.copy(
                        showSearch = event.show,
                        searchQuery = if (event.show) "" else it.searchQuery,
                        hasFocused = if (event.show) false else it.hasFocused
                    )
                }
            }

            is HistoryEvent.OnRequestFocus -> {
                if (!_state.value.hasFocused) {
                    _state.update { it.copy(hasFocused = true) }
                    send(HistoryEffect.OnRequestFocus)
                }
            }

            is HistoryEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
                search.type(event.query)
            }

            is HistoryEvent.OnSearch -> {
                if (_state.value.showSearch) search.submit() else search.clear()
            }

            is HistoryEvent.OnDeleteHistoryEntry -> {
                viewModelScope.launch {
                    deleteHistoryUseCase(event.history)
                    _effects.emit(HistoryEffect.OnShowSnackbar(event.history))
                }
            }

            is HistoryEvent.OnRestoreHistoryEntry -> {
                viewModelScope.launch { addHistoryUseCase(event.history) }
            }

            is HistoryEvent.OnShowDeleteWholeHistoryDialog -> {
                _state.update { it.copy(dialog = HistoryScreen.DELETE_WHOLE_HISTORY_DIALOG) }
            }

            is HistoryEvent.OnActionDeleteWholeHistoryDialog -> {
                // No loading state: an empty history deletes no rows, and with
                // no change the list would never come back to clear it.
                _state.update { it.copy(dialog = null, showSearch = false) }
                search.clear()

                viewModelScope.launch {
                    deleteWholeHistoryUseCase()
                    _effects.emit(HistoryEffect.OnWholeHistoryDeleted)
                }
            }

            is HistoryEvent.OnDismissDialog -> {
                _state.update { it.copy(dialog = null) }
            }

            is HistoryEvent.OnNavigateToLibrary -> {
                send(HistoryEffect.OnNavigateToLibrary)
            }

            is HistoryEvent.OnNavigateToStatistics -> {
                send(HistoryEffect.OnNavigateToStatistics)
            }

            is HistoryEvent.OnNavigateToBookInfo -> {
                send(HistoryEffect.OnNavigateToBookInfo(event.bookId))
            }

            is HistoryEvent.OnNavigateToReader -> {
                send(HistoryEffect.OnNavigateToReader(event.bookId))
            }
        }
    }
}
