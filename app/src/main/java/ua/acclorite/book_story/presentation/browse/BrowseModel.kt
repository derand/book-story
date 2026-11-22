/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.helpers.ListSearch
import ua.acclorite.book_story.core.helpers.launchWhileSubscribed
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.file.SourceLocality
import ua.acclorite.book_story.domain.use_case.book.AddBookUseCase
import ua.acclorite.book_story.domain.use_case.book.StoreBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetBookFromFileUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetFilesUseCase
import ua.acclorite.book_story.domain.use_case.file_system.ObserveLibraryFilesUseCase
import ua.acclorite.book_story.presentation.browse.model.AddingBooks
import ua.acclorite.book_story.presentation.browse.model.BrowseListOptions
import ua.acclorite.book_story.presentation.browse.model.NullableBook
import ua.acclorite.book_story.presentation.browse.model.SelectableFile
import ua.acclorite.book_story.presentation.browse.model.SelectableNullableBook
import ua.acclorite.book_story.presentation.browse.model.browseListing
import ua.acclorite.book_story.presentation.browse.model.toggled
import ua.acclorite.book_story.presentation.browse.model.withSelected
import ua.acclorite.book_story.presentation.library.LibraryScreen
import javax.inject.Inject

@HiltViewModel
class BrowseModel @Inject constructor(
    private val addBookUseCase: AddBookUseCase,
    private val storeBookCopyUseCase: StoreBookCopyUseCase,
    private val getFilesUseCase: GetFilesUseCase,
    private val getBookFromFileUseCase: GetBookFromFileUseCase,
    private val getBookSourcesUseCase: GetBookSourcesUseCase,
    private val observeLibraryFilesUseCase: ObserveLibraryFilesUseCase,
    private val settings: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BrowseEffect>()
    val effects = _effects.asSharedFlow()

    private val search = ListSearch(viewModelScope)

    /**
     * The last walk of the granted folders, or null before the first one.
     *
     * Everything Browse shows is narrowed from it in memory: a walk is a query
     * per directory — over the network for a cloud folder — and it used to run
     * at every start, on every visit to the tab, on every pause in typing a
     * search and after every sort, filter or library change.
     */
    private val walked = MutableStateFlow<Result<List<File>>?>(null)

    init {
        // Only while the screen shows the list: this model lives as long as
        // the activity, and the library it filters by changes on every
        // settled scroll in the reader.
        viewModelScope.launchWhileSubscribed(_state) {
            // The first visit, not the model's creation, is what walks: the
            // model is created at start, whether Browse is ever opened or not.
            if (walked.value == null && walkJob?.isActive != true) {
                refresh(loading = true, hideSearch = false)
            }

            val options = combine(
                search.filter,
                settings.browseIncludedFilterItems.flow,
                settings.browseSortOrder.flow,
                settings.browseSortOrderDescending.flow,
                ::BrowseListOptions
            )
            combine(
                walked.filterNotNull(),
                observeLibraryFilesUseCase(),
                options
            ) { walk, existing, listOptions ->
                walk.mapCatching { files ->
                    files.browseListing(existing.getOrThrow(), listOptions)
                }
            }.flowOn(Dispatchers.Default).collect { listing -> show(listing) }
        }

        // The folders changed, so the walk is out of date. Walked again now if
        // the list is on screen, on the next visit otherwise.
        viewModelScope.launch {
            BrowseScreen.refreshListChannel.receiveAsFlow().collectLatest {
                if (_state.subscriptionCount.value > 0) {
                    refresh(loading = false, hideSearch = false)
                } else {
                    walkJob?.cancel()
                    walked.value = null
                }
            }
        }
    }

    private var addBooksJob: Job? = null
    private var walkJob: Job? = null
    private var getAddDialogBooksJob: Job? = null

    private fun send(effect: BrowseEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    /**
     * Replaces the selection, and the flag that follows from it. The count
     * keeps its last value at zero: the top bar still shows it while it
     * animates away.
     */
    private fun select(change: (List<SelectableFile>) -> List<SelectableFile>) {
        _state.update {
            val files = change(it.files)
            val count = files.count { file -> file.selected }
            it.copy(
                files = files,
                selectedItemsCount = if (count == 0) it.selectedItemsCount else count,
                hasSelectedItems = count > 0
            )
        }
    }

    /**
     * Walks the folders again; a walk already running gives way to this one,
     * and stops at its next directory.
     */
    private fun refresh(loading: Boolean, hideSearch: Boolean) {
        _state.update {
            it.copy(
                isRefreshing = true,
                isLoading = loading || it.isLoading,
                showSearch = if (hideSearch) false else it.showSearch
            )
        }
        if (hideSearch) search.clear()

        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            val result = getFilesUseCase()

            // Only when the folders hold nothing at all, because that is the
            // only thing the counts decide: which of the two empty states this
            // is. Asked unconditionally they would cost a round trip per
            // granted tree on every walk, against a provider that answers over
            // a network.
            val sources = when {
                result.getOrNull()?.isEmpty() == true -> getBookSourcesUseCase()
                else -> null
            }

            // A newer walk has the last word, not this one.
            ensureActive()
            _state.update {
                it.copy(
                    sourcesGranted = sources?.size ?: it.sourcesGranted,
                    sourcesAvailable = sources?.count { source ->
                        source.isAvailable
                    } ?: it.sourcesAvailable
                )
            }
            walked.value = result

            delay(500) // Long enough for the indicator to be seen
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Shows a new listing. The files that were selected and are still listed
     * stay selected: a book added elsewhere, or a search typed further, is no
     * reason to lose a selection.
     */
    private fun show(listing: Result<List<File>>) {
        _state.update { state ->
            val selected = state.files.filter { it.selected }.mapTo(HashSet()) { it.uri }
            val files = listing.getOrDefault(emptyList()).map { file ->
                SelectableFile(
                    name = file.name,
                    uri = file.uri,
                    path = file.path,
                    size = file.size,
                    lastModified = file.lastModified,
                    selected = file.uri in selected
                )
            }
            val count = files.count { it.selected }
            state.copy(
                files = files,
                loadFailed = listing.isFailure,
                selectedItemsCount = if (count == 0) state.selectedItemsCount else count,
                hasSelectedItems = count > 0,
                isLoading = false
            )
        }
    }

    /**
     * Handled in the order sent: state changes at once, and only the work
     * that waits (the files, the database, an effect) is launched.
     */
    fun onEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.OnRefreshList -> {
                refresh(loading = event.loading, hideSearch = event.hideSearch)
            }

            is BrowseEvent.OnSearchVisibility -> {
                if (!event.show) search.clear()
                _state.update {
                    it.copy(
                        showSearch = event.show,
                        searchQuery = if (event.show) "" else it.searchQuery,
                        hasFocused = if (event.show) false else it.hasFocused
                    )
                }
            }

            is BrowseEvent.OnRequestFocus -> {
                if (!_state.value.hasFocused) {
                    _state.update { it.copy(hasFocused = true) }
                    send(BrowseEffect.OnRequestFocus)
                }
            }

            is BrowseEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
                search.type(event.query)
            }

            is BrowseEvent.OnSearch -> {
                if (_state.value.showSearch) search.submit() else search.clear()
            }

            is BrowseEvent.OnClearSelectedFiles -> {
                _state.update {
                    it.copy(
                        files = it.files.map { file -> file.copy(selected = false) },
                        hasSelectedItems = false
                    )
                }
            }

            is BrowseEvent.OnToggleFile -> select { it.toggled(event.uri) }

            is BrowseEvent.OnSelectFiles -> select { it.withSelected(event.files) }

            is BrowseEvent.OnShowFilterBottomSheet -> {
                _state.update { it.copy(bottomSheet = BrowseScreen.FILTER_BOTTOM_SHEET) }
            }

            is BrowseEvent.OnDismissBottomSheet -> {
                _state.update { it.copy(bottomSheet = null) }
            }

            is BrowseEvent.OnShowAddDialog -> {
                _state.update {
                    it.copy(
                        dialog = BrowseScreen.ADD_DIALOG,
                        selectedBooksAddDialog = emptyList(),
                        loadingAddDialog = true
                    )
                }

                val files = _state.value.files.filter { it.selected }
                getAddDialogBooksJob?.cancel()
                getAddDialogBooksJob = viewModelScope.launch(Dispatchers.IO) {
                    val books = files.map { file ->
                        ensureActive()

                        val nullableBook = getBookFromFileUseCase(
                            File(
                                name = file.name,
                                uri = file.uri,
                                path = file.path,
                                size = file.size,
                                lastModified = file.lastModified,
                                isDirectory = false
                            )
                        ).let {
                            if (it != null) {
                                NullableBook.NotNull(it.first, it.second)
                            } else {
                                NullableBook.Null(
                                    fileName = file.name,
                                    UIText.StringResource(resId = R.string.error_something_went_wrong)
                                )
                            }
                        }

                        SelectableNullableBook(
                            data = nullableBook,
                            selected = true
                        )
                    }

                    ensureActive()
                    _state.update {
                        if (books.isEmpty()) {
                            it.copy(loadingAddDialog = false, dialog = null)
                        } else {
                            it.copy(selectedBooksAddDialog = books, loadingAddDialog = false)
                        }
                    }
                }
            }

            is BrowseEvent.OnDismissAddDialog -> {
                _state.update { it.copy(dialog = null) }
                getAddDialogBooksJob?.cancel()
            }

            is BrowseEvent.OnCancelAddingBooks -> {
                addBooksJob?.cancel()
            }

            is BrowseEvent.OnActionAddDialog -> {
                addBooksJob?.cancel()
                addBooksJob = viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val books = _state.value.selectedBooksAddDialog.mapNotNull {
                            if (it.data is NullableBook.NotNull && it.selected) {
                                return@mapNotNull it.data
                            }
                            return@mapNotNull null
                        }
                        if (books.isEmpty()) return@launch

                        // The same rule the dialog showed the switch by, over
                        // the same list: what was on screen is what happens.
                        // When it is on, it applies to every book selected —
                        // the user asked to keep their own copies, not for the
                        // app to decide which ones deserve one.
                        val keepCopy = settings.keepLocalCopy.lastValue &&
                                books.any {
                                    SourceLocality.offersLocalCopy(
                                        it.book.documentAuthority
                                    )
                                }

                        books.forEachIndexed { index, nullableBook ->
                            // Cancellation is looked for between books, not
                            // inside one: a copy is blocking I/O with nowhere
                            // to notice it, and a book half in the library
                            // would be worse than one book more than asked for.
                            ensureActive()

                            _state.update {
                                it.copy(
                                    addingBooks = AddingBooks(
                                        done = index,
                                        total = books.size,
                                        title = nullableBook.book.title
                                    )
                                )
                            }

                            val id = addBookUseCase(
                                nullableBook.book,
                                nullableBook.coverImage
                            )
                            if (keepCopy && id != null) {
                                // A copy that could not be made leaves the book
                                // reading its original in place: worth less than
                                // was asked for, but never worth losing the book
                                // over.
                                storeBookCopyUseCase(nullableBook.book.copy(id = id))
                            }
                        }

                        // Only a run that finished takes the user to what it
                        // produced. A cancelled one leaves them where they
                        // stopped it, with the books it did add already there.
                        withContext(NonCancellable) {
                            _effects.emit(BrowseEffect.OnNavigateToLibrary)
                            _effects.emit(BrowseEffect.OnBooksAdded)
                        }
                    } finally {
                        // Runs for a cancelled add too: whatever was added is
                        // in the library, and the screen has to stop claiming
                        // otherwise.
                        withContext(NonCancellable) {
                            _state.update {
                                it.copy(
                                    addingBooks = null,
                                    dialog = null
                                )
                            }
                            LibraryScreen.scrollToPageCompositionChannel.trySend(0)
                            // No walk: the books added leave the list as the
                            // library they went into changes.
                            onEvent(BrowseEvent.OnClearSelectedFiles)
                        }
                    }
                }
            }

            is BrowseEvent.OnSelectAddDialog -> {
                _state.update {
                    val index = it.selectedBooksAddDialog.indexOf(event.book)
                    if (index == -1) return@update it

                    val editedList = it.selectedBooksAddDialog.toMutableList()
                    editedList[index] = editedList[index].let { book ->
                        book.copy(selected = !book.selected)
                    }

                    // At least one readable book stays ticked.
                    if (editedList.none { book -> book.data is NullableBook.NotNull && book.selected }) {
                        return@update it
                    }

                    it.copy(selectedBooksAddDialog = editedList)
                }
            }

            is BrowseEvent.OnUpdatePinnedPaths -> {
                send(BrowseEffect.OnUpdatePinnedPaths(event.path))
            }

            is BrowseEvent.OnNavigateToLibrary -> {
                send(BrowseEffect.OnNavigateToLibrary)
            }

            is BrowseEvent.OnNavigateToBrowseSettings -> {
                send(BrowseEffect.OnNavigateToBrowseSettings)
            }
        }
    }
}
