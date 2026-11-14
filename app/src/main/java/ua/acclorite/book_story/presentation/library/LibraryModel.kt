/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library

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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.helpers.launchWhileSubscribed
import ua.acclorite.book_story.domain.use_case.backup.DismissRestoreNoticeUseCase
import ua.acclorite.book_story.domain.use_case.backup.GetRestoreNoticeUseCase
import ua.acclorite.book_story.domain.use_case.book.DeleteBookUseCase
import ua.acclorite.book_story.domain.use_case.book.LibraryBooks
import ua.acclorite.book_story.domain.use_case.book.ObserveLibraryBooksUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateBookUseCase
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.library.model.SelectableBook
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class LibraryModel @Inject constructor(
    private val updateBookUseCase: UpdateBookUseCase,
    private val observeLibraryBooksUseCase: ObserveLibraryBooksUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val getRestoreNoticeUseCase: GetRestoreNoticeUseCase,
    private val dismissRestoreNoticeUseCase: DismissRestoreNoticeUseCase
) : ViewModel() {

    private val mutex = Mutex()

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LibraryEffect>()
    val effects = _effects.asSharedFlow()

    /** What the list is narrowed to: the query once typing pauses, while search is shown. */
    private val searchFilter = MutableStateFlow("")

    /** Bumped by pull-to-refresh, which reads the list again from scratch. */
    private val reload = MutableStateFlow(0)

    init {
        // The list follows the database, so nothing has to say "refresh" after
        // a write; but only while the screen shows it: this model lives as long
        // as the activity, and the reader writes on every settled scroll.
        viewModelScope.launchWhileSubscribed(_state) {
            reload.collectLatest {
                observeLibraryBooksUseCase(searchFilter)
                    .flowOn(Dispatchers.Default)
                    .collect { result ->
                        result.fold(
                            onSuccess = { books -> showBooks(books) },
                            onFailure = { showLoadFailed() }
                        )
                    }
            }
        }

        // The first start after a restore says what happened: a restart that
        // explains nothing looks like a crash.
        viewModelScope.launch {
            val notice = getRestoreNoticeUseCase() ?: return@launch
            _state.update { it.copy(restoreNotice = notice) }
        }
    }

    /** A fresh read replaces the list; the books that were selected stay selected. */
    private suspend fun showBooks(books: LibraryBooks) {
        _state.update { state ->
            val selected = state.books.filter { it.selected }.mapTo(HashSet()) { it.data.id }
            val list = books.matching.map { book -> SelectableBook(book, book.id in selected) }
            val selectedCount = list.count { it.selected }
            state.copy(
                loadFailed = false,
                library = books.all,
                books = list,
                selectedItemsCount = selectedCount,
                hasSelectedItems = selectedCount > 0,
                isLoading = false
            )
        }
    }

    private suspend fun showLoadFailed() {
        _state.update {
            it.copy(
                library = emptyList(),
                books = emptyList(),
                selectedItemsCount = 0,
                hasSelectedItems = false,
                loadFailed = true,
                isLoading = false
            )
        }
    }

    private var refreshJob: Job? = null
    private var searchQueryChange: Job? = null

    fun onEvent(event: LibraryEvent) {
        viewModelScope.launch {
            when (event) {
                is LibraryEvent.OnRefreshList -> {
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

                is LibraryEvent.OnSearchVisibility -> {
                    if (!event.show) searchFilter.value = ""

                    _state.update {
                        it.copy(
                            showSearch = event.show,
                            searchQuery = if (event.show) "" else it.searchQuery,
                            hasFocused = if (event.show) false else it.hasFocused
                        )
                    }
                }

                is LibraryEvent.OnSearchQueryChange -> {
                    _state.update {
                        it.copy(
                            searchQuery = event.query
                        )
                    }
                    searchQueryChange?.cancel()
                    searchQueryChange = viewModelScope.launch(Dispatchers.IO) {
                        delay(500)
                        onEvent(LibraryEvent.OnSearch)
                    }
                }

                is LibraryEvent.OnSearch -> {
                    searchFilter.value =
                        if (_state.value.showSearch) _state.value.searchQuery else ""
                }

                is LibraryEvent.OnRequestFocus -> {
                    if (!_state.value.hasFocused) {
                        _effects.emit(LibraryEffect.OnRequestFocus)
                        _state.update {
                            it.copy(
                                hasFocused = true
                            )
                        }
                    }
                }

                is LibraryEvent.OnClearSelectedBooks -> {
                    withContext(Dispatchers.Default) {
                        _state.update {
                            it.copy(
                                books = it.books.map { book -> book.copy(selected = false) },
                                hasSelectedItems = false
                            )
                        }
                    }
                }

                is LibraryEvent.OnSelectBook -> {
                    withContext(Dispatchers.Default) {
                        val editedList = _state.value.books.map {
                            if (it.data.id == event.id) it.copy(
                                selected = event.select ?: !it.selected
                            )
                            else it
                        }

                        _state.update {
                            it.copy(
                                books = editedList,
                                selectedItemsCount = editedList.filter { book -> book.selected }.size,
                                hasSelectedItems = editedList.any { book -> book.selected }
                            )
                        }
                    }
                }

                is LibraryEvent.OnSelectBooks -> {
                    withContext(Dispatchers.Default) {
                        val editedList = _state.value.books.map { book ->
                            if (event.books.any { book.data.id == it.data.id }) book.copy(
                                selected = if (event.books.size > 1) true else !book.selected
                            )
                            else book
                        }

                        _state.update {
                            it.copy(
                                books = editedList,
                                selectedItemsCount = editedList.filter { book -> book.selected }.size,
                                hasSelectedItems = editedList.any { book -> book.selected }
                            )
                        }
                    }
                }

                is LibraryEvent.OnShowMoveDialog -> {
                    _state.update {
                        it.copy(
                            dialog = LibraryScreen.MOVE_DIALOG
                        )
                    }
                }

                is LibraryEvent.OnActionMoveDialog -> {
                    withContext(Dispatchers.Default) {
                        val categories = event.selectedCategories.map { it.id }
                        val moved = updateBookUseCase.categories(
                            bookIds = _state.value.books.filter { it.selected }.map { it.data.id },
                            categories = categories
                        )

                        _state.update {
                            it.copy(
                                books = it.books.map { book ->
                                    if (!book.selected) return@map book
                                    book.copy(
                                        data = if (moved) book.data.copy(categories = categories)
                                        else book.data,
                                        selected = false
                                    )
                                },
                                hasSelectedItems = false,
                                dialog = null
                            )
                        }

                        if (!moved) {
                            _effects.emit(LibraryEffect.OnErrorMoveBooks)
                            return@withContext
                        }
                        _effects.emit(LibraryEffect.OnBooksMoved)
                    }
                }

                is LibraryEvent.OnShowDeleteDialog -> {
                    _state.update {
                        it.copy(
                            dialog = LibraryScreen.DELETE_DIALOG
                        )
                    }
                }

                is LibraryEvent.OnActionDeleteDialog -> {
                    withContext(Dispatchers.Default) {
                        _state.value.books.forEach {
                            if (!it.selected) return@forEach
                            deleteBookUseCase(it.data)
                        }

                        _state.update {
                            it.copy(
                                books = it.books.filter { book -> !book.selected },
                                hasSelectedItems = false,
                                dialog = null
                            )
                        }

                        BrowseScreen.refreshListChannel.trySend(Unit)
                        _effects.emit(LibraryEffect.OnBooksDeleted)
                    }
                }

                is LibraryEvent.OnDismissDialog -> {
                    _state.update {
                        it.copy(
                            dialog = null
                        )
                    }
                }

                is LibraryEvent.OnDismissRestoreNotice -> {
                    _state.update { it.copy(restoreNotice = null) }
                    dismissRestoreNoticeUseCase()
                }

                is LibraryEvent.OnRestoreNoticeAddFolders -> {
                    _state.update { it.copy(restoreNotice = null) }
                    dismissRestoreNoticeUseCase()
                    _effects.emit(LibraryEffect.OnNavigateToBrowseSettings)
                }

                is LibraryEvent.OnShowFilterBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = LibraryScreen.FILTER_BOTTOM_SHEET
                        )
                    }
                }

                is LibraryEvent.OnDismissBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = null
                        )
                    }
                }

                is LibraryEvent.OnNavigateToLibrarySettings -> {
                    _effects.emit(LibraryEffect.OnNavigateToLibrarySettings)
                }

                is LibraryEvent.OnNavigateToBrowse -> {
                    _effects.emit(LibraryEffect.OnNavigateToBrowse)
                }

                is LibraryEvent.OnNavigateToBookInfo -> {
                    _effects.emit(LibraryEffect.OnNavigateToBookInfo(event.id))
                }

                is LibraryEvent.OnNavigateToReader -> {
                    _effects.emit(LibraryEffect.OnNavigateToReader(event.id))
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