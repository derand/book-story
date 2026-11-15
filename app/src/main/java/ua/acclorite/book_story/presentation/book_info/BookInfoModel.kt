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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        if (event is BookInfoEvent.OnToggleLocalCopy || event is BookInfoEvent.OnRefreshLocalCopy) {
            if (copying) return
            copying = true
            _state.update { it.copy(changingLocalCopy = true) }
        }

        viewModelScope.launch {
            val book = _state.value.book
            val id = book.id
            when (event) {
                is BookInfoEvent.OnShowDetailsBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = BookInfoScreen.DETAILS_BOTTOM_SHEET
                        )
                    }
                }

                is BookInfoEvent.OnShowChangeCoverBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = BookInfoScreen.CHANGE_COVER_BOTTOM_SHEET
                        )
                    }
                }

                is BookInfoEvent.OnChangeCover -> {
                    withContext(Dispatchers.Default) {
                        updateCoverImageUseCase(id, event.image)
                        val updatedCoverImage = getBookUseCase(id)?.coverImage
                            ?: return@withContext
                        val canResetCover = canResetCoverImageUseCase(id)

                        updateFor(id) {
                            it.copy(
                                book = it.book.copy(
                                    coverImage = updatedCoverImage
                                ),
                                bottomSheet = null,
                                canResetCover = canResetCover
                            )
                        }

                        emitFor(id, BookInfoEffect.OnChangedCover)
                    }
                }

                is BookInfoEvent.OnResetCover -> {
                    withContext(Dispatchers.Default) {
                        val result = resetCoverImageUseCase(id)
                        if (!result) {
                            emitFor(id, BookInfoEffect.OnErrorResetCover)
                            return@withContext
                        }

                        val reset = getBookUseCase(id)
                        if (reset == null) {
                            emitFor(id, BookInfoEffect.OnErrorResetCover)
                            return@withContext
                        }

                        updateFor(id) {
                            it.copy(
                                book = reset,
                                bottomSheet = null,
                                canResetCover = false
                            )
                        }

                        emitFor(id, BookInfoEffect.OnResetCover)
                    }
                }

                is BookInfoEvent.OnDeleteCover -> {
                    withContext(Dispatchers.Default) {
                        updateCoverImageUseCase(id, null)
                        val canResetCover = canResetCoverImageUseCase(id)
                        updateFor(id) {
                            it.copy(
                                book = it.book.copy(
                                    coverImage = null
                                ),
                                bottomSheet = null,
                                canResetCover = canResetCover
                            )
                        }

                        emitFor(id, BookInfoEffect.OnDeletedCover)
                    }
                }

                is BookInfoEvent.OnCheckCoverReset -> {
                    withContext(Dispatchers.Default) {
                        if (id == -1) return@withContext
                        val canResetCover = canResetCoverImageUseCase(id)
                        updateFor(id) {
                            it.copy(
                                canResetCover = canResetCover
                            )
                        }
                    }
                }

                is BookInfoEvent.OnSetFinished -> {
                    withContext(Dispatchers.Default) {
                        if (id == -1) return@withContext

                        setBookFinishedUseCase(
                            book = book,
                            finished = event.finished
                        )
                        updateFor(id) {
                            it.copy(
                                statistics = it.statistics?.copy(finished = event.finished)
                            )
                        }
                    }
                }

                is BookInfoEvent.OnDismissBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = null
                        )
                    }
                }

                is BookInfoEvent.OnShowTitleDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.TITLE_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionTitleDialog -> {
                    withContext(Dispatchers.Default) {
                        if (!updateBookUseCase.title(id, event.title)) {
                            emitFor(id, BookInfoEffect.OnErrorUpdate)
                            return@withContext
                        }
                        updateFor(id) {
                            it.copy(
                                book = it.book.copy(
                                    title = event.title
                                )
                            )
                        }

                        emitFor(id, BookInfoEffect.OnTitleChanged)
                    }
                }

                is BookInfoEvent.OnShowAuthorDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.AUTHOR_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionAuthorDialog -> {
                    withContext(Dispatchers.Default) {
                        // Stored the way the row has it: "unknown author" is no
                        // author at all.
                        val author = event.author.getAsString() ?: ""
                        if (!updateBookUseCase.author(id, author)) {
                            emitFor(id, BookInfoEffect.OnErrorUpdate)
                            return@withContext
                        }
                        updateFor(id) {
                            it.copy(
                                book = it.book.copy(
                                    author = event.author
                                )
                            )
                        }

                        emitFor(id, BookInfoEffect.OnAuthorChanged)
                    }
                }

                is BookInfoEvent.OnShowDescriptionDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.DESCRIPTION_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionDescriptionDialog -> {
                    withContext(Dispatchers.Default) {
                        val description = event.description
                        if (!updateBookUseCase.description(id, description)) {
                            emitFor(id, BookInfoEffect.OnErrorUpdate)
                            return@withContext
                        }
                        updateFor(id) {
                            it.copy(
                                book = it.book.copy(
                                    description = event.description
                                )
                            )
                        }

                        emitFor(id, BookInfoEffect.OnDescriptionChanged)
                    }
                }

                is BookInfoEvent.OnShowPathDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.PATH_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionPathDialog -> {
                    withContext(Dispatchers.Default) {
                        if (!updateBookUseCase.filePath(id, event.path)) {
                            emitFor(id, BookInfoEffect.OnErrorUpdate)
                            return@withContext
                        }
                        updateFor(id) {
                            // As the row now has it: a path that changed took
                            // the old place's identity with it.
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
                        }

                        emitFor(id, BookInfoEffect.OnPathChanged)

                        val file = getFileFromBookUseCase(id)
                        updateFor(id) {
                            it.copy(
                                file = file,
                                loadingFile = false
                            )
                        }
                    }
                }

                is BookInfoEvent.OnShowDeleteDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.DELETE_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionDeleteDialog -> {
                    withContext(Dispatchers.Default) {
                        updateFor(id) {
                            it.copy(
                                dialog = null,
                                bottomSheet = null
                            )
                        }
                        deleteBookUseCase(book)

                        BrowseScreen.refreshListChannel.trySend(Unit)

                        emitFor(id, BookInfoEffect.OnBookDeleted)
                        emitFor(id, BookInfoEffect.OnNavigateBack)
                    }
                }

                is BookInfoEvent.OnToggleLocalCopy -> {
                    try {
                        withContext(Dispatchers.Default) {
                            val changed =
                                if (book.isOwnCopy) releaseBookCopyUseCase(book)
                                else storeBookCopyUseCase(book)

                            if (changed == null) {
                                emitFor(id, BookInfoEffect.OnErrorLocalCopy)
                            } else {
                                updateFor(id) {
                                    it.copy(
                                        book = changed,
                                        loadingFile = true
                                    )
                                }

                                // The book reads from somewhere else now, so what the
                                // details say about its file is about the wrong one.
                                val file = getFileFromBookUseCase(id)
                                updateFor(id) {
                                    it.copy(
                                        file = file,
                                        loadingFile = false
                                    )
                                }
                            }
                        }
                    } finally {
                        localCopyDone()
                    }
                }

                is BookInfoEvent.OnRefreshLocalCopy -> {
                    try {
                        withContext(Dispatchers.Default) {
                            val refreshed = refreshBookCopyUseCase(book)
                            if (refreshed == null) {
                                emitFor(id, BookInfoEffect.OnErrorLocalCopy)
                            } else {
                                updateFor(id) {
                                    it.copy(
                                        book = refreshed,
                                        loadingFile = true
                                    )
                                }
                                val file = getFileFromBookUseCase(id)
                                updateFor(id) {
                                    it.copy(
                                        file = file,
                                        loadingFile = false
                                    )
                                }
                                emitFor(id, BookInfoEffect.OnLocalCopyRefreshed)
                            }
                        }
                    } finally {
                        localCopyDone()
                    }
                }

                is BookInfoEvent.OnShowMoveDialog -> {
                    _state.update {
                        it.copy(
                            dialog = BookInfoScreen.MOVE_DIALOG
                        )
                    }
                }

                is BookInfoEvent.OnActionMoveDialog -> {
                    withContext(Dispatchers.Default) {
                        val categories = event.selectedCategories.map { it.id }
                        val moved = updateBookUseCase.categories(
                            listOf(id),
                            categories
                        )
                        updateFor(id) {
                            it.copy(
                                book = if (moved) it.book.copy(categories = categories)
                                else it.book,
                                dialog = null,
                                bottomSheet = null
                            )
                        }
                        if (!moved) {
                            emitFor(id, BookInfoEffect.OnErrorUpdate)
                            return@withContext
                        }

                        emitFor(id, BookInfoEffect.OnBookMoved)
                    }
                }

                is BookInfoEvent.OnDismissDialog -> {
                    _state.update {
                        it.copy(
                            dialog = null
                        )
                    }
                }

                is BookInfoEvent.OnNavigateBack -> {
                    _effects.emit(BookInfoEffect.OnNavigateBack)
                }

                is BookInfoEvent.OnNavigateToLibrarySettings -> {
                    _effects.emit(BookInfoEffect.OnNavigateToLibrarySettings)
                }

                is BookInfoEvent.OnNavigateToReader -> {
                    _effects.emit(BookInfoEffect.OnNavigateToReader)
                }
            }
        }
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

            if (changePath) onEvent(BookInfoEvent.OnShowPathDialog)
            onEvent(BookInfoEvent.OnCheckCoverReset)

            val statistics = getBookStatisticsUseCase(bookId)
            updateFor(bookId) { it.copy(statistics = statistics) }

            val file = getFileFromBookUseCase(bookId)
            updateFor(bookId) {
                it.copy(
                    file = file,
                    loadingFile = false
                )
            }
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

    /** On Main, after the copy's work: no other copy runs from here on. */
    private fun localCopyDone() {
        copying = false
        _state.update { it.copy(changingLocalCopy = false) }
    }
}
