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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.coroutineContext

@HiltViewModel
class HistoryModel @Inject constructor(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val addHistoryUseCase: AddHistoryUseCase,
    private val observeLibraryStatisticsUseCase: ObserveLibraryStatisticsUseCase,
    private val deleteHistoryUseCase: DeleteHistoryUseCase,
    private val deleteWholeHistoryUseCase: DeleteWholeHistoryUseCase,
    private val getBookUseCase: GetBookUseCase
) : ViewModel() {

    private val mutex = Mutex()

    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<HistoryEffect>()
    val effects = _effects.asSharedFlow()

    /** What the list is narrowed to: the query once typing pauses, while search is shown. */
    private val searchFilter = MutableStateFlow("")

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
                    observeHistoryUseCase(searchFilter),
                    observeLibraryStatisticsUseCase()
                ) { history, statistics -> history to statistics }
                    .flowOn(Dispatchers.Default)
                    .collect { (history, statistics) ->
                        _state.update {
                            it.copy(
                                history = history,
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

    private var refreshJob: Job? = null
    private var searchQueryChange: Job? = null

    fun onEvent(event: HistoryEvent) {
        viewModelScope.launch {
            when (event) {
                is HistoryEvent.OnRefreshList -> {
                    refreshJob?.cancel()
                    refreshJob = viewModelScope.launch {
                        _state.update {
                            it.copy(
                                isRefreshing = true,
                                isLoading = event.loading,
                                showSearch = if (event.hideSearch) false else it.showSearch
                            )
                        }
                        if (event.hideSearch) searchFilter.value = ""
                        reload.value++

                        delay(500) // Delay for UI smoothness
                        _state.update {
                            it.copy(
                                isRefreshing = false
                            )
                        }
                    }
                }

                is HistoryEvent.OnSearchVisibility -> {
                    if (!event.show) searchFilter.value = ""

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
                        _effects.emit(HistoryEffect.OnRequestFocus)
                        _state.update {
                            it.copy(
                                hasFocused = true
                            )
                        }
                    }
                }

                is HistoryEvent.OnSearchQueryChange -> {
                    _state.update {
                        it.copy(
                            searchQuery = event.query
                        )
                    }

                    searchQueryChange?.cancel()
                    searchQueryChange = viewModelScope.launch(Dispatchers.IO) {
                        delay(500)
                        onEvent(HistoryEvent.OnSearch)
                    }
                }

                is HistoryEvent.OnSearch -> {
                    searchFilter.value =
                        if (_state.value.showSearch) _state.value.searchQuery else ""
                }

                is HistoryEvent.OnDeleteHistoryEntry -> {
                    withContext(Dispatchers.Default) {
                        deleteHistoryUseCase(event.history)
                        _effects.emit(HistoryEffect.OnShowSnackbar(event.history))
                    }
                }

                is HistoryEvent.OnRestoreHistoryEntry -> {
                    withContext(Dispatchers.Default) {
                        addHistoryUseCase(event.history)
                    }
                }

                is HistoryEvent.OnShowDeleteWholeHistoryDialog -> {
                    _state.update {
                        it.copy(
                            dialog = HistoryScreen.DELETE_WHOLE_HISTORY_DIALOG
                        )
                    }
                }

                is HistoryEvent.OnActionDeleteWholeHistoryDialog -> {
                    withContext(Dispatchers.Default) {
                        // No loading state: an empty history deletes no rows,
                        // and with no change the list would never come back
                        // to clear it.
                        _state.update {
                            it.copy(
                                dialog = null,
                                showSearch = false
                            )
                        }
                        searchFilter.value = ""

                        deleteWholeHistoryUseCase()

                        _effects.emit(HistoryEffect.OnWholeHistoryDeleted)
                    }
                }

                is HistoryEvent.OnDismissDialog -> {
                    _state.update {
                        it.copy(
                            dialog = null
                        )
                    }
                }

                is HistoryEvent.OnNavigateToLibrary -> {
                    _effects.emit(HistoryEffect.OnNavigateToLibrary)
                }

                is HistoryEvent.OnNavigateToStatistics -> {
                    _effects.emit(HistoryEffect.OnNavigateToStatistics)
                }

                is HistoryEvent.OnNavigateToBookInfo -> {
                    _effects.emit(HistoryEffect.OnNavigateToBookInfo(event.bookId))
                }

                is HistoryEvent.OnNavigateToReader -> {
                    _effects.emit(HistoryEffect.OnNavigateToReader(event.bookId))
                }
            }
        }
    }

    private suspend inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
        mutex.withLock {
            coroutineContext.ensureActive()
            this.value = function(this.value)
        }
    }
}