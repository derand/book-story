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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.core.helpers.ListSearch
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

@HiltViewModel
class LibraryModel @Inject constructor(
    private val updateBookUseCase: UpdateBookUseCase,
    private val observeLibraryBooksUseCase: ObserveLibraryBooksUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val getRestoreNoticeUseCase: GetRestoreNoticeUseCase,
    private val dismissRestoreNoticeUseCase: DismissRestoreNoticeUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LibraryEffect>()
    val effects = _effects.asSharedFlow()

    private val search = ListSearch(viewModelScope)

    /** Bumped by pull-to-refresh, which reads the list again from scratch. */
    private val reload = MutableStateFlow(0)

    init {
        // The list follows the database, so nothing has to say "refresh" after
        // a write; but only while the screen shows it: this model lives as long
        // as the activity, and the reader writes on every settled scroll.
        viewModelScope.launchWhileSubscribed(_state) {
            reload.collectLatest {
                observeLibraryBooksUseCase(search.filter)
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
    private fun showBooks(books: LibraryBooks) {
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

    private fun showLoadFailed() {
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

    private fun send(effect: LibraryEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    /** Replaces the selection flags, and the counts that follow from them. */
    private fun select(selected: (SelectableBook) -> Boolean) {
        _state.update { state ->
            val books = state.books.map { it.copy(selected = selected(it)) }
            val count = books.count { it.selected }
            state.copy(
                books = books,
                selectedItemsCount = count,
                hasSelectedItems = count > 0
            )
        }
    }

    private var refreshJob: Job? = null

    /**
     * Handled in the order sent: state changes at once, and only the work
     * that waits (the database, an effect) is launched.
     */
    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.OnRefreshList -> {
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

            is LibraryEvent.OnSearchVisibility -> {
                if (!event.show) search.clear()
                _state.update {
                    it.copy(
                        showSearch = event.show,
                        searchQuery = if (event.show) "" else it.searchQuery,
                        hasFocused = if (event.show) false else it.hasFocused
                    )
                }
            }

            is LibraryEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
                search.type(event.query)
            }

            is LibraryEvent.OnSearch -> {
                if (_state.value.showSearch) search.submit() else search.clear()
            }

            is LibraryEvent.OnRequestFocus -> {
                if (!_state.value.hasFocused) {
                    _state.update { it.copy(hasFocused = true) }
                    send(LibraryEffect.OnRequestFocus)
                }
            }

            is LibraryEvent.OnClearSelectedBooks -> {
                _state.update {
                    it.copy(
                        books = it.books.map { book -> book.copy(selected = false) },
                        hasSelectedItems = false
                    )
                }
            }

            is LibraryEvent.OnSelectBook -> {
                select { book ->
                    if (book.data.id == event.id) event.select ?: !book.selected
                    else book.selected
                }
            }

            is LibraryEvent.OnSelectBooks -> {
                val ids = event.books.mapTo(HashSet()) { it.data.id }
                select { book ->
                    if (book.data.id !in ids) book.selected
                    else if (event.books.size > 1) true
                    else !book.selected
                }
            }

            is LibraryEvent.OnShowMoveDialog -> {
                _state.update { it.copy(dialog = LibraryScreen.MOVE_DIALOG) }
            }

            is LibraryEvent.OnActionMoveDialog -> {
                val categories = event.selectedCategories.map { it.id }
                val bookIds = _state.value.books.filter { it.selected }.map { it.data.id }
                viewModelScope.launch {
                    val moved = updateBookUseCase.categories(
                        bookIds = bookIds,
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

                    _effects.emit(
                        if (moved) LibraryEffect.OnBooksMoved else LibraryEffect.OnErrorMoveBooks
                    )
                }
            }

            is LibraryEvent.OnShowDeleteDialog -> {
                _state.update { it.copy(dialog = LibraryScreen.DELETE_DIALOG) }
            }

            is LibraryEvent.OnActionDeleteDialog -> {
                val books = _state.value.books.filter { it.selected }.map { it.data }
                // Off the main thread: removing a cover is plain file I/O.
                viewModelScope.launch(Dispatchers.IO) {
                    books.forEach { deleteBookUseCase(it) }

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
                _state.update { it.copy(dialog = null) }
            }

            is LibraryEvent.OnDismissRestoreNotice -> {
                _state.update { it.copy(restoreNotice = null) }
                viewModelScope.launch { dismissRestoreNoticeUseCase() }
            }

            is LibraryEvent.OnRestoreNoticeAddFolders -> {
                _state.update { it.copy(restoreNotice = null) }
                viewModelScope.launch {
                    dismissRestoreNoticeUseCase()
                    _effects.emit(LibraryEffect.OnNavigateToBrowseSettings)
                }
            }

            is LibraryEvent.OnShowFilterBottomSheet -> {
                _state.update { it.copy(bottomSheet = LibraryScreen.FILTER_BOTTOM_SHEET) }
            }

            is LibraryEvent.OnDismissBottomSheet -> {
                _state.update { it.copy(bottomSheet = null) }
            }

            is LibraryEvent.OnNavigateToLibrarySettings -> {
                send(LibraryEffect.OnNavigateToLibrarySettings)
            }

            is LibraryEvent.OnNavigateToBrowse -> {
                send(LibraryEffect.OnNavigateToBrowse)
            }

            is LibraryEvent.OnNavigateToBookInfo -> {
                send(LibraryEffect.OnNavigateToBookInfo(event.id))
            }

            is LibraryEvent.OnNavigateToReader -> {
                send(LibraryEffect.OnNavigateToReader(event.id))
            }
        }
    }
}
