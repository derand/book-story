/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.book_info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.use_case.book.CanResetCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.book.DeleteBookUseCase
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import ua.acclorite.book_story.domain.use_case.book.GetFileFromBookUseCase
import ua.acclorite.book_story.domain.use_case.book.RefreshBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.ReleaseBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.ResetCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.book.StoreBookCopyUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateBookUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateCoverImageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.GetBookStatisticsUseCase
import ua.acclorite.book_story.domain.use_case.statistics.SetBookFinishedUseCase
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import javax.inject.Inject

@HiltViewModel
class BookInfoModel @Inject constructor(
    private val updateCoverImageUseCase: UpdateCoverImageUseCase,
    private val updateBookUseCase: UpdateBookUseCase,
    private val getBookUseCase: GetBookUseCase,
    private val getFileFromBookUseCase: GetFileFromBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val canResetCoverImageUseCase: CanResetCoverImageUseCase,
    private val resetCoverImageUseCase: ResetCoverImageUseCase,
    private val getBookStatisticsUseCase: GetBookStatisticsUseCase,
    private val setBookFinishedUseCase: SetBookFinishedUseCase,
    private val storeBookCopyUseCase: StoreBookCopyUseCase,
    private val releaseBookCopyUseCase: ReleaseBookCopyUseCase,
    private val refreshBookCopyUseCase: RefreshBookCopyUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BookInfoState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BookInfoEffect>()
    val effects = _effects.asSharedFlow()

    /** The read [init] runs; read-only, so a newer [init] or [leave] may cancel it. */
    private var initJob: Job? = null

    /**
     * A local copy is being made, given up or refreshed. Set and tested on Main
     * in [onEvent], so a second tap is ignored even within one frame, and kept
     * across a leave, so the switch is still off when the book is opened again.
     */
    @Volatile
    private var copying = false

    /*
     * Writes are never cancelled: Back right after an edit must not lose it. The
     * model outlives the screen (one per activity), so what a write finds when it
     * ends may be another book's screen, or none: its state changes and effects
     * go through [updateFor] / [emitFor], which drop them unless the book it
     * started on is still the one shown.
     */
    fun onEvent(event: BookInfoEvent) {
        when (event) {
            is BookInfoEvent.OnShowDialog -> _state.update { it.copy(dialog = event.dialog) }
            is BookInfoEvent.OnDismissDialog -> _state.update { it.copy(dialog = null) }
            is BookInfoEvent.OnShowBottomSheet -> _state.update { it.copy(bottomSheet = event.sheet) }
            is BookInfoEvent.OnDismissBottomSheet -> _state.update { it.copy(bottomSheet = null) }

            is BookInfoEvent.OnNavigateBack -> emit(BookInfoEffect.OnNavigateBack)
            is BookInfoEvent.OnNavigateToLibrarySettings ->
                emit(BookInfoEffect.OnNavigateToLibrarySettings)
            is BookInfoEvent.OnNavigateToReader -> emit(BookInfoEffect.OnNavigateToReader)

            is BookInfoEvent.OnToggleLocalCopy, is BookInfoEvent.OnRefreshLocalCopy -> {
                if (copying) return
                copying = true
                _state.update { it.copy(changingLocalCopy = true) }
                write { book ->
                    try {
                        if (event is BookInfoEvent.OnToggleLocalCopy) toggleLocalCopy(book)
                        else refreshLocalCopy(book)
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main) { localCopyDone() }
                    }
                }
            }

            is BookInfoEvent.OnChangeCover -> write { book ->
                updateCoverImageUseCase(book.id, event.image)
                val coverImage = getBookUseCase(book.id)?.coverImage ?: return@write
                val canResetCover = canResetCoverImageUseCase(book.id)
                updateFor(book.id) {
                    it.copy(
                        book = it.book.copy(coverImage = coverImage),
                        bottomSheet = null,
                        canResetCover = canResetCover
                    )
                }
                emitFor(book.id, BookInfoEffect.OnChangedCover)
            }

            is BookInfoEvent.OnResetCover -> write { book ->
                val reset = resetCoverImageUseCase(book.id)
                    .let { done -> if (done) getBookUseCase(book.id) else null }
                if (reset == null) {
                    emitFor(book.id, BookInfoEffect.OnErrorResetCover)
                    return@write
                }
                updateFor(book.id) {
                    it.copy(book = reset, bottomSheet = null, canResetCover = false)
                }
                emitFor(book.id, BookInfoEffect.OnResetCover)
            }

            is BookInfoEvent.OnDeleteCover -> write { book ->
                updateCoverImageUseCase(book.id, null)
                val canResetCover = canResetCoverImageUseCase(book.id)
                updateFor(book.id) {
                    it.copy(
                        book = it.book.copy(coverImage = null),
                        bottomSheet = null,
                        canResetCover = canResetCover
                    )
                }
                emitFor(book.id, BookInfoEffect.OnDeletedCover)
            }

            is BookInfoEvent.OnSetFinished -> write { book ->
                setBookFinishedUseCase(book = book, finished = event.finished)
                updateFor(book.id) {
                    it.copy(statistics = it.statistics?.copy(finished = event.finished))
                }
            }

            is BookInfoEvent.OnActionTitleDialog -> write { book ->
                edit(
                    book.id,
                    write = { updateBookUseCase.title(book.id, event.title) },
                    change = { it.copy(book = it.book.copy(title = event.title)) },
                    done = BookInfoEffect.OnTitleChanged
                )
            }

            is BookInfoEvent.OnActionAuthorDialog -> write { book ->
                // Stored the way the row has it: "unknown author" is no author
                // at all.
                val author = event.author.getAsString() ?: ""
                edit(
                    book.id,
                    write = { updateBookUseCase.author(book.id, author) },
                    change = { it.copy(book = it.book.copy(author = event.author)) },
                    done = BookInfoEffect.OnAuthorChanged
                )
            }

            is BookInfoEvent.OnActionDescriptionDialog -> write { book ->
                edit(
                    book.id,
                    write = { updateBookUseCase.description(book.id, event.description) },
                    change = { it.copy(book = it.book.copy(description = event.description)) },
                    done = BookInfoEffect.OnDescriptionChanged
                )
            }

            is BookInfoEvent.OnActionPathDialog -> write { book ->
                val changed = edit(
                    book.id,
                    write = { updateBookUseCase.filePath(book.id, event.path) },
                    change = {
                        // As the row now has it: a path that changed took the
                        // old place's identity with it.
                        val moved = it.book.filePath != event.path
                        it.copy(
                            book = it.book.copy(
                                filePath = event.path,
                                documentAuthority = it.book.documentAuthority
                                    .takeUnless { moved },
                                documentId = it.book.documentId.takeUnless { moved }
                            ),
                            loadingFile = true
                        )
                    },
                    done = BookInfoEffect.OnPathChanged
                )
                if (changed) loadFile(book.id)
            }

            is BookInfoEvent.OnActionMoveDialog -> write { book ->
                updateFor(book.id) { it.copy(dialog = null, bottomSheet = null) }
                val categories = event.selectedCategories.map { it.id }
                edit(
                    book.id,
                    write = { updateBookUseCase.categories(listOf(book.id), categories) },
                    change = { it.copy(book = it.book.copy(categories = categories)) },
                    done = BookInfoEffect.OnBookMoved
                )
            }

            is BookInfoEvent.OnActionDeleteDialog -> write { book ->
                updateFor(book.id) { it.copy(dialog = null, bottomSheet = null) }
                deleteBookUseCase(book)
                BrowseScreen.refreshListChannel.trySend(Unit)
                emitFor(book.id, BookInfoEffect.OnBookDeleted)
                emitFor(book.id, BookInfoEffect.OnNavigateBack)
            }
        }
    }

    /**
     * Runs [block] off Main for the book shown now. Reading the book here, on
     * Main and before anything suspends, is what ties the write to the book the
     * user acted on.
     */
    private fun write(block: suspend (Book) -> Unit) {
        val book = _state.value.book
        viewModelScope.launch {
            withContext(Dispatchers.Default) { block(book) }
        }
    }

    private fun emit(effect: BookInfoEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    /**
     * One edit of the row: [write] it, and only if that worked show it ([change])
     * and say so ([done]). Answers whether it was written.
     */
    private suspend fun edit(
        bookId: Int,
        write: suspend () -> Boolean,
        change: (BookInfoState) -> BookInfoState,
        done: BookInfoEffect
    ): Boolean {
        if (!write()) {
            emitFor(bookId, BookInfoEffect.OnErrorUpdate)
            return false
        }
        updateFor(bookId, change)
        emitFor(bookId, done)
        return true
    }

    private suspend fun toggleLocalCopy(book: Book) {
        val changed =
            if (book.isOwnCopy) releaseBookCopyUseCase(book)
            else storeBookCopyUseCase(book)

        if (changed == null) {
            emitFor(book.id, BookInfoEffect.OnErrorLocalCopy)
            return
        }
        // The book reads from somewhere else now, so what the details say about
        // its file is about the wrong one.
        updateFor(book.id) { it.copy(book = changed, loadingFile = true) }
        loadFile(book.id)
    }

    private suspend fun refreshLocalCopy(book: Book) {
        val refreshed = refreshBookCopyUseCase(book)
        if (refreshed == null) {
            emitFor(book.id, BookInfoEffect.OnErrorLocalCopy)
            return
        }
        updateFor(book.id) { it.copy(book = refreshed, loadingFile = true) }
        loadFile(book.id)
        emitFor(book.id, BookInfoEffect.OnLocalCopyRefreshed)
    }

    private suspend fun loadFile(bookId: Int) {
        val file = getFileFromBookUseCase(bookId)
        updateFor(bookId) { it.copy(file = file, loadingFile = false) }
    }

    fun init(
        bookId: Int,
        changePath: Boolean
    ) {
        initJob?.cancel()
        initJob = viewModelScope.launch(Dispatchers.Default) {
            val book = getBookUseCase(bookId)

            if (book == null) {
                _effects.emit(BookInfoEffect.OnNavigateBack)
                return@launch
            }

            _state.update {
                BookInfoState(
                    book = book,
                    changingLocalCopy = copying
                )
            }

            if (changePath) {
                updateFor(bookId) { it.copy(dialog = BookInfoState.Dialog.Path) }
            }

            // Alongside: it may parse the book for its own cover.
            launch {
                val canResetCover = canResetCoverImageUseCase(bookId)
                updateFor(bookId) { it.copy(canResetCover = canResetCover) }
            }

            val statistics = getBookStatisticsUseCase(bookId)
            updateFor(bookId) { it.copy(statistics = statistics) }

            loadFile(bookId)
        }
    }

    /**
     * The screen is gone. What it started keeps running (see [onEvent]); only
     * the state is let go, so nothing that ends from here on lands anywhere.
     */
    fun leave() {
        initJob?.cancel()
        _state.update { BookInfoState() }
    }

    private inline fun updateFor(bookId: Int, function: (BookInfoState) -> BookInfoState) {
        _state.update { if (it.book.id == bookId) function(it) else it }
    }

    private suspend fun emitFor(bookId: Int, effect: BookInfoEffect) {
        if (_state.value.book.id == bookId) _effects.emit(effect)
    }

    /** On Main, after the copy's work: a next tap starts another one. */
    private fun localCopyDone() {
        copying = false
        _state.update { it.copy(changingLocalCopy = false) }
    }
}
