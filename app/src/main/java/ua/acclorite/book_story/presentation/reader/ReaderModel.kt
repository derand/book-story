/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.domain.model.library.Book
import androidx.core.net.toUri
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.log.BookOpenTrace
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.reader.BookImageStore
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.domain.model.reader.SEARCH_MIN_QUERY_LENGTH
import ua.acclorite.book_story.domain.model.reader.findSearchMatches
import ua.acclorite.book_story.domain.model.statistics.wordCount
import ua.acclorite.book_story.domain.model.statistics.wordPrefixSums
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import ua.acclorite.book_story.domain.use_case.book.AddPreviewToLibraryUseCase
import ua.acclorite.book_story.domain.use_case.book.DiscardPreviewsUseCase
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import ua.acclorite.book_story.domain.use_case.book.GetChapterProgressUseCase
import ua.acclorite.book_story.domain.use_case.book.GetTextUseCase
import ua.acclorite.book_story.domain.use_case.book.KeepOnlyBookImagesUseCase
import ua.acclorite.book_story.domain.use_case.book.LoadBookImagesUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateBookUseCase
import ua.acclorite.book_story.domain.use_case.permission.GrantPersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.history.GetHistoryForBookUseCase
import ua.acclorite.book_story.domain.use_case.statistics.GetBookCoverageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.RecordReadingSessionUseCase
import ua.acclorite.book_story.domain.use_case.statistics.SaveBookCoverageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.UpdateReadBookUseCase
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.reader.model.ReadingSessionTracker
import ua.acclorite.book_story.presentation.reader.model.readingProgress
import ua.acclorite.book_story.presentation.reader.model.stepTarget
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * How long the typing has to stop before the book is scanned. Long enough that
 * an ordinary word is scanned once rather than once per letter.
 */
private const val SEARCH_DEBOUNCE = 300L

/**
 * How far down the screen a match is put. Landing it at the very top hides it
 * under the search bar — the text is drawn behind the bars — and landing it much
 * lower would put it under the keyboard. A share of the viewport is the only
 * measurement this layer has: the bar's height belongs to the composition.
 */
private const val SEARCH_LANDING_FRACTION = 0.3f

@HiltViewModel
class ReaderModel @Inject constructor(
    private val updateBookUseCase: UpdateBookUseCase,
    private val getTextUseCase: GetTextUseCase,
    private val getBookSourcesUseCase: GetBookSourcesUseCase,
    private val getBookUseCase: GetBookUseCase,
    private val getHistoryForBookUseCase: GetHistoryForBookUseCase,
    private val getChapterProgressUseCase: GetChapterProgressUseCase,
    private val loadBookImagesUseCase: LoadBookImagesUseCase,
    private val keepOnlyBookImagesUseCase: KeepOnlyBookImagesUseCase,
    private val recordReadingSessionUseCase: RecordReadingSessionUseCase,
    private val getBookCoverageUseCase: GetBookCoverageUseCase,
    private val saveBookCoverageUseCase: SaveBookCoverageUseCase,
    private val updateReadBookUseCase: UpdateReadBookUseCase,
    private val addPreviewToLibraryUseCase: AddPreviewToLibraryUseCase,
    private val discardPreviewsUseCase: DiscardPreviewsUseCase,
    private val grantPersistableUriPermissionUseCase: GrantPersistableUriPermissionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state = _state.asStateFlow()

    /**
     * Where the open book's images live. Kept out of [state] on purpose: it is
     * observable on its own, so filling an image in repaints just that image
     * instead of pushing a new text list through the whole reader.
     */
    val imageStore = BookImageStore()
    private var imageJob: Job? = null

    /**
     * Image bytes a fresh parse produced, held only until [ReaderEvent.OnLoadImages]
     * has handed them over. Kept off [state] so they are not pinned for the whole
     * session by the text that no longer needs them.
     */
    private var parsedImageBytes: Map<String, ByteArray> = emptyMap()

    private val _effects = MutableSharedFlow<ReaderEffect>()
    val effects = _effects.asSharedFlow()

    private var scrollJob: Job? = null
    private var searchJob: Job? = null

    /**
     * Parent of every [onEvent] job, so that [clear] can stop the running ones.
     * A job rather than a list of them: a finished child leaves on its own.
     * Supervisor, so that one failed event does not cancel the others.
     *
     * Every handler, and every job the model starts, runs on Main, so the
     * model's fields and the list state are only ever touched from there. The
     * passes over the whole book (parse, image split, word counts, search)
     * go to Default and hand back a value; they change nothing themselves.
     */
    private val eventJobs = SupervisorJob(viewModelScope.coroutineContext.job)

    /** The statistics ledger; see [ReadingSessionTracker]. */
    private val tracker = ReadingSessionTracker(
        itemWords = { index -> _state.value.text.getOrNull(index)?.wordCount() },
        noteWords = { id -> _state.value.notes[id]?.wordCount() ?: 0 }
    )
    private var coverageJob: Job? = null

    /** The Keep in progress, for [ReaderEvent.OnLeave] to wait for. */
    private var keepJob: Job? = null

    fun onEvent(event: ReaderEvent) {
        viewModelScope.launch(eventJobs) {
            when (event) {
                is ReaderEvent.OnLoadText -> {
                    // The parse and the image split below walk the whole book,
                    // so they run on Default and only hand back what they made.
                    // Everything is applied here, on Main, like every other
                    // change to the model.
                    val parsedText = withContext(Dispatchers.Default) {
                        getTextUseCase(_state.value.book.id)
                    }
                    val text = parsedText.text
                    BookOpenTrace.mark("text parsed")
                    ensureActive()

                    if (text.isEmpty()) {
                        val failure = failureMessage(_state.value.book)
                        _state.updateIfActive {
                            it.copy(
                                isLoading = false,
                                errorMessage = failure.first,
                                errorOffersPathChange = failure.second
                            )
                        }
                        _effects.emit(ReaderEffect.OnSystemBarsVisibility(show = true))
                        return@launch
                    }

                    _effects.emit(ReaderEffect.OnSystemBarsVisibility(show = null))

                    // A freshly parsed book carries its image bytes, one
                    // restored from the cache carries metadata only. Either
                    // way the bytes are kept out of the state: [OnLoadImages]
                    // hands them to the store in the background, which holds
                    // a bounded few and puts the rest on disk. Holding them
                    // here as well would pin tens of megabytes for the whole
                    // session.
                    val split = withContext(Dispatchers.Default) { splitImageBytes(text) }
                    parsedImageBytes = split.bytes
                    imageStore.reset(split.imageSrcs)

                    val lastOpened = getHistoryForBookUseCase(_state.value.book.id)?.time
                    _state.updateIfActive {
                        it.copy(
                            showMenu = false,
                            book = it.book.copy(
                                lastOpened = lastOpened
                            ),
                            text = split.text,
                            notes = parsedText.notes
                        )
                    }
                    BookOpenTrace.mark("text in state")
                    ensureActive()

                    // The session starts when there is something to read,
                    // not when the book was opened: a cold parse is not
                    // reading time.
                    startSession()

                    // Separately, because counting every word of the book is
                    // one pass over all of it and the first frame is still
                    // ahead — [OnRestoreScroll] below is what ends the
                    // loading placeholder.
                    coverageJob = viewModelScope.launch {
                        loadCoverage(split.text)
                    }

                    // No row write here: nothing about the row changed (the
                    // `lastOpened` above is not a column), and writing back
                    // the copy read before the parse could only undo
                    // something written meanwhile. The lists are refreshed
                    // by the history insert that came with the open.

                    onEvent(ReaderEvent.OnRestoreScroll)
                }

                is ReaderEvent.OnLoadImages -> {
                    if (imageJob?.isActive == true) return@launch
                    val pending = imageStore.pending()
                    if (pending.isEmpty()) return@launch

                    val bookId = _state.value.book.id
                    val parsed = parsedImageBytes
                    imageJob = viewModelScope.launch {
                        // The pass switches to IO itself and calls back from
                        // there; the store is snapshot state and takes that.
                        loadBookImagesUseCase(bookId, pending, parsed) { src, image ->
                            imageStore.put(src, image)
                        }
                        // Written out, so the last reference to them can go.
                        parsedImageBytes = emptyMap()
                        // Whatever the pass did not resolve is not coming.
                        imageStore.finish()
                    }
                }

                is ReaderEvent.OnRestoreScroll -> {
                    snapshotFlow { _state.value.listState.layoutInfo.totalItemsCount }.first { it > 0 }

                    _state.value.listState.requestScrollToItem(
                        index = _state.value.book.scrollIndex,
                        scrollOffset = _state.value.book.scrollOffset
                    )

                    _state.updateIfActive {
                        val (currentChapter, currentChapterProgress) = getChapterProgressUseCase(
                            it.book.scrollIndex,
                            it.text
                        )
                        it.copy(
                            currentChapter = currentChapter,
                            currentChapterProgress = currentChapterProgress,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }

                is ReaderEvent.OnMenuVisibility -> {
                    if (_state.value.lockMenu) return@launch

                    _effects.emit(
                        ReaderEffect.OnSystemBarsVisibility(
                            show = if (event.show) true
                            else null
                        )
                    )

                    if (event.saveCheckpoint && event.show) {
                        val checkpoints = _state.value.checkpoints.toMutableList()
                        checkpoints.removeIf {
                            it.index == _state.value.listState.firstVisibleItemIndex
                        }
                        checkpoints.add(
                            Checkpoint(
                                _state.value.listState.firstVisibleItemIndex,
                                _state.value.listState.firstVisibleItemScrollOffset
                            )
                        )

                        _state.updateIfActive {
                            it.copy(checkpoints = checkpoints)
                        }
                    }

                    _state.updateIfActive {
                        it.copy(showMenu = event.show)
                    }

                    // With the search open, the bars going away is the
                    // reader turning back to the text — and coming back is
                    // the search covering it again.
                    if (_state.value.search.active) {
                        if (event.show) tracker.overlayShown() else tracker.overlayHidden()
                    }
                }

                is ReaderEvent.OnDragProgress -> {
                    // Shown only. The list moves to it in [OnScroll], and the
                    // row is written once it settles there ([updateProgress]),
                    // with the position that is actually on screen: writing the
                    // dragged value now would pair it with the old position,
                    // and a book left before the list moved reopened there with
                    // its progress jumping back.
                    _state.updateIfActive {
                        it.copy(book = it.book.copy(progress = event.progress))
                    }
                }

                is ReaderEvent.OnUpdateChapter -> {
                    _state.updateIfActive {
                        val (currentChapter, currentChapterProgress) = getChapterProgressUseCase(
                            event.index,
                            _state.value.text
                        )
                        it.copy(
                            currentChapter = currentChapter,
                            currentChapterProgress = currentChapterProgress
                        )
                    }
                }

                is ReaderEvent.OnScrollToChapter -> {
                    val chapterIndex = _state.value.text
                        .indexOf(event.chapter)
                        .takeIf { it != -1 }
                    if (chapterIndex == null) return@launch

                    clearCurrentMatch()
                    _state.value.listState.requestScrollToItem(
                        index = chapterIndex,
                        scrollOffset = 0
                    )

                    // The new position is saved by [updateProgress] once the
                    // list lands: measured here, before the next layout, the
                    // progress would describe the screen being left.
                    onEvent(ReaderEvent.OnUpdateChapter(chapterIndex))
                }

                is ReaderEvent.OnScroll -> {
                    scrollJob?.cancel()
                    scrollJob = viewModelScope.launch {
                        delay(300)

                        val scrollTo = (_state.value.text.lastIndex * event.progress).roundToInt()

                        clearCurrentMatch()
                        jumpTo(index = scrollTo, offset = 0)

                        onEvent(ReaderEvent.OnUpdateChapter(scrollTo))
                    }
                }

                is ReaderEvent.OnRestoreCheckpoint -> {
                    _state.updateIfActive {
                        val checkpoints = it.checkpoints.toMutableList()
                        if (checkpoints.size > 1) checkpoints.remove(event.checkpoint)

                        it.copy(
                            checkpoints = checkpoints
                        )
                    }

                    clearCurrentMatch()
                    _state.value.listState.requestScrollToItem(
                        index = event.checkpoint.index,
                        scrollOffset = event.checkpoint.offset
                    )

                    // Saved by [updateProgress] once the list lands, as for
                    // a chapter.
                    onEvent(ReaderEvent.OnUpdateChapter(event.checkpoint.index))
                }

                is ReaderEvent.OnSearchVisibility -> {
                    searchJob?.cancel()

                    when (event.show) {
                        true -> {
                            tracker.creditOpenNote()
                            tracker.overlayShown()
                            _state.updateIfActive {
                                it.copy(
                                    search = ReaderSearch(
                                        active = true,
                                        origin = Checkpoint(
                                            index = it.listState.firstVisibleItemIndex,
                                            offset = it.listState.firstVisibleItemScrollOffset
                                        )
                                    ),
                                    drawer = null
                                )
                            }
                        }

                        // "Stay here": the search ends and the reader keeps
                        // whatever position it took them to.
                        false -> {
                            tracker.overlayHidden()
                            _state.updateIfActive { it.copy(search = ReaderSearch()) }
                        }
                    }
                }

                is ReaderEvent.OnSearchQueryChange -> {
                    searchJob?.cancel()

                    // Typing is the reader at work, like opening a note: without
                    // this, time spent composing a query and reading the count
                    // would be measured from the last scroll and cut off the end
                    // of the session by the idle cap.
                    tracker.markActive()

                    val query = event.query
                    val searchable = query.trim().length >= SEARCH_MIN_QUERY_LENGTH
                    _state.updateIfActive {
                        it.copy(
                            search = it.search.copy(
                                query = query,
                                // Not knowing the count yet is not the same as
                                // there being none, and the bar says so.
                                scanning = searchable,
                                matches = emptyList(),
                                current = -1
                            )
                        )
                    }
                    if (!searchable) return@launch

                    searchJob = viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE)

                        val text = _state.value.text
                        val notes = _state.value.notes
                        val matches = withContext(Dispatchers.Default) {
                            text.findSearchMatches(query = query, notes = notes)
                        }
                        ensureActive()

                        _state.updateIfActive {
                            // A query that moved on while this scan ran owns the
                            // bar now; these matches are for a string nobody typed.
                            if (it.search.query != query) return@updateIfActive it
                            it.copy(
                                search = it.search.copy(
                                    matches = matches,
                                    current = -1,
                                    scanning = false
                                )
                            )
                        }
                    }
                }

                is ReaderEvent.OnSearchStep -> {
                    val target = _state.value.search.stepTarget(
                        forward = event.forward,
                        visible = visibleRange()
                    ) ?: return@launch
                    val match = _state.value.search.matches[target]

                    _state.updateIfActive {
                        it.copy(search = it.search.copy(current = target))
                    }

                    // Stepping through matches lands on screens nobody reads,
                    // exactly like dragging the slider does.
                    jumpTo(
                        index = match.itemIndex,
                        // Negative: the item is pushed *down* the screen,
                        // out from under the bar that was used to find it.
                        offset = searchLandingOffset()
                    )
                    // The landing is saved like any settled position
                    // ([updateProgress]); [OnSearchReturn] brings the reader
                    // back to where the search started.
                    onEvent(ReaderEvent.OnUpdateChapter(match.itemIndex))
                }

                is ReaderEvent.OnSearchReturn -> {
                    searchJob?.cancel()

                    val origin = _state.value.search.origin
                    tracker.overlayHidden()
                    _state.updateIfActive { it.copy(search = ReaderSearch()) }

                    if (origin != null) {
                        // Not a jump for the statistics: this lands where the
                        // reading resumes, and that screen gets no other sample.
                        _state.value.listState.requestScrollToItem(
                            index = origin.index,
                            scrollOffset = origin.offset
                        )
                        onEvent(ReaderEvent.OnUpdateChapter(origin.index))
                    }
                }

                is ReaderEvent.OnLeave -> {
                    // Once only. The reader stays on screen until the leave
                    // navigates, and a second Back in that time would run it all
                    // again and pop whatever is below the reader as well.
                    if (_state.value.lockMenu) return@launch
                    _state.updateIfActive {
                        it.copy(
                            lockMenu = true
                        )
                    }

                    // A Keep still copying decides what leaving means: the book
                    // is either kept by the time it ends, and must not be
                    // discarded, or it is not, and must be. Waited for before
                    // the session ends, because a Keep that succeeds starts one.
                    keepJob?.join()

                    // Before the position-saving block below, which is guarded
                    // by conditions of its own and must not decide whether a
                    // session was read.
                    endSession()

                    if (
                        !_state.value.isLoading &&
                        _state.value.listState.layoutInfo.totalItemsCount > 0 &&
                        _state.value.text.isNotEmpty() &&
                        _state.value.errorMessage != null
                    ) {
                        _state.updateIfActive {
                            it.copy(
                                book = it.book.copy(
                                    progress = calculateProgress(),
                                    scrollIndex = _state.value.listState.firstVisibleItemIndex,
                                    scrollOffset = _state.value.listState.firstVisibleItemScrollOffset
                                )
                            )
                        }

                        _state.value.book.let { book ->
                            updateBookUseCase.position(
                                book.id, book.scrollIndex, book.scrollOffset, book.progress
                            )
                        }
                    }

                    // Leaving a book that was only being previewed is the user
                    // declining it. Nothing about it is kept — the row, the
                    // cover and the image files all go, and the statistics never
                    // held anything to begin with.
                    discardPreviewsUseCase.discard(_state.value.book)

                    // System bars and brightness are restored by the screen's
                    // own onDispose, which every way out of the reader passes.
                    event.navigate()
                }

                is ReaderEvent.OnAddToLibrary -> {
                    keep()
                }

                is ReaderEvent.OnGrantFolder -> {
                    grantPersistableUriPermissionUseCase(event.uri)
                    keep()
                }

                is ReaderEvent.OnOpenTranslator -> {
                    _effects.emit(
                        ReaderEffect.OnOpenTranslator(
                            textToTranslate = event.textToTranslate,
                            translateWholeParagraph = event.translateWholeParagraph
                        )
                    )
                }

                is ReaderEvent.OnOpenShareApp -> {
                    _effects.emit(
                        ReaderEffect.OnOpenShareApp(
                            textToShare = event.textToShare
                        )
                    )
                }

                is ReaderEvent.OnOpenWebBrowser -> {
                    _effects.emit(
                        ReaderEffect.OnOpenWebBrowser(
                            textToSearch = event.textToSearch
                        )
                    )
                }

                is ReaderEvent.OnShowSettingsBottomSheet -> {
                    tracker.creditOpenNote()
                    tracker.overlayShown()
                    _state.updateIfActive {
                        it.copy(
                            bottomSheet = ReaderScreen.SETTINGS_BOTTOM_SHEET,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnOpenNote -> {
                    val id = event.tag.substringAfter(':')
                    val note = _state.value.notes[id] ?: return@launch

                    // Whatever it replaces stops covering the text, and the
                    // note sheet itself never counts as an overlay: its words
                    // are credited, so its time is reading time.
                    tracker.overlayHidden()
                    tracker.noteOpened(id)
                    _state.updateIfActive {
                        it.copy(
                            bottomSheet = ReaderScreen.NOTE_BOTTOM_SHEET,
                            currentNote = note,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnOpenImage -> {
                    tracker.creditOpenNote()
                    tracker.overlayShown()
                    _state.updateIfActive {
                        it.copy(
                            fullscreenImage = event.image,
                            bottomSheet = null,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnDismissImage -> {
                    tracker.overlayHidden()
                    _state.updateIfActive {
                        it.copy(
                            fullscreenImage = null
                        )
                    }
                    // What it was opened over may still be covering the text.
                    resumeOverlayIfCovered()
                }

                is ReaderEvent.OnDismissBottomSheet -> {
                    tracker.creditOpenNote()
                    tracker.overlayHidden()
                    _state.updateIfActive {
                        it.copy(
                            bottomSheet = null
                        )
                    }
                    resumeOverlayIfCovered()
                }

                is ReaderEvent.OnShowChaptersDrawer -> {
                    tracker.creditOpenNote()
                    tracker.overlayShown()
                    _state.updateIfActive {
                        it.copy(
                            drawer = ReaderScreen.CHAPTERS_DRAWER,
                            bottomSheet = null
                        )
                    }
                }

                is ReaderEvent.OnDismissDrawer -> {
                    tracker.overlayHidden()
                    _state.updateIfActive {
                        it.copy(
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnNavigateBack -> {
                    _effects.emit(ReaderEffect.OnNavigateBack)
                }

                is ReaderEvent.OnNavigateToBookInfo -> {
                    _effects.emit(ReaderEffect.OnNavigateToBookInfo(event.changePath))
                }
            }
        }
    }

    fun init(bookId: Int) {
        viewModelScope.launch {
            val book = timed("  init: book row") { getBookUseCase(bookId) }

            if (book == null) {
                // The commonest way to arrive here is a preview that did not
                // survive the process: the start-up sweep took its row, and the
                // navigator restored a screen pointing at it. Leaving silently
                // looks like the app closing itself for no reason.
                _effects.emit(ReaderEffect.OnBookGone)
                _effects.emit(ReaderEffect.OnNavigateBack)
                return@launch
            }

            timed("  init: clear previous") { clear() }
            // Here rather than when the previous reader closed: a book keeps its
            // image files so that reopening it needs no work, and this is the
            // point where they stop being the ones worth keeping.
            timed("  init: drop other images") { keepOnlyBookImagesUseCase(bookId) }

            _state.updateIfActive {
                ReaderState(
                    book = book
                )
            }

            onEvent(ReaderEvent.OnLoadText)
        }
    }

    /**
     * The reader became visible. Starts a session if there is text to read —
     * on the way in the text is not loaded yet and [ReaderEvent.OnLoadText]
     * starts it instead, but coming back from a call or the home screen lands
     * here with the book already in memory.
     */
    fun onEnterForeground() {
        startSession()
    }

    /**
     * The reader stopped being visible — a call, the home gesture, the screen
     * going off, another app. All of them mean the reading stopped, and none of
     * them is a deliberate exit, so nothing else would have recorded it.
     */
    fun onLeaveForeground() {
        viewModelScope.launch { endSession() }
    }

    /**
     * Runs [addToLibrary] once at a time, and not at all once the reader is
     * being left. The copy can take seconds: a second Keep in that time would
     * copy the file again, and a Back would discard the book being kept.
     */
    private suspend fun keep() {
        if (_state.value.addingToLibrary || _state.value.lockMenu) return
        _state.updateIfActive { it.copy(addingToLibrary = true) }
        keepJob = currentCoroutineContext().job
        try {
            addToLibrary()
        } finally {
            keepJob = null
            _state.updateIfActive { it.copy(addingToLibrary = false) }
        }
    }

    /**
     * Keeps the book being previewed, and starts measuring it.
     *
     * Statistics begin here rather than at the open: the minutes spent deciding
     * whether the book was worth reading are not credited retroactively, which
     * is the same statement as not recording them in the first place. Coverage
     * has to be loaded now for the same reason — the pass at open time declined
     * to, so without this the session would bank time and no words.
     */
    private suspend fun addToLibrary() {
        val book = _state.value.book
        if (book.inLibrary) return

        when (val result = addPreviewToLibraryUseCase(book)) {
            is AddPreviewToLibraryUseCase.Result.Added -> {
                // Everything the promotion changed, and nothing else: progress
                // may have moved while the copy was being made. Nothing here
                // writes the row back whole any more, but the reader opens its
                // file from this copy, so it has to name where the book now is.
                _state.updateIfActive {
                    it.copy(
                        book = it.book.copy(
                            inLibrary = true,
                            previewUri = null,
                            filePath = result.book.filePath
                        )
                    )
                }

                startSession()
                coverageJob = viewModelScope.launch {
                    loadCoverage(_state.value.text)
                }

                _effects.emit(ReaderEffect.OnAddedToLibrary)
            }

            is AddPreviewToLibraryUseCase.Result.NeedsGrant -> {
                _effects.emit(ReaderEffect.OnRequestFolderGrant(result.folder))
            }

            is AddPreviewToLibraryUseCase.Result.Failed -> {
                _effects.emit(ReaderEffect.OnCannotAddToLibrary)
            }
        }
    }

    /**
     * What to tell the reader when a book produced no text, and whether offering
     * to re-point it could help.
     *
     * The reason never reaches this screen — every failure arrives as an empty
     * text — but the book carries its authority, and the sources can be asked
     * afterwards. That separates three situations the one old message ran
     * together:
     *
     * - the source is quiet. Nothing about the book is wrong and nothing about
     *   its path is wrong, so it says so and offers no path to change;
     * - no app publishes that authority any anymore. Nothing is coming back, and
     *   pointing the book at a file it can still reach is the only thing that
     *   helps — so that is offered, and the wait is not suggested;
     * - anything else keeps the old message, which is honest about not knowing.
     */
    private suspend fun failureMessage(book: Book): Pair<UIText, Boolean> {
        val couldNotGetText =
            UIText.StringResource(R.string.error_could_not_get_text) to true
        val authority = book.documentAuthority ?: return couldNotGetText

        val sources = getBookSourcesUseCase().filter { it.authority == authority }
        if (sources.isEmpty() || sources.any { it.isAvailable }) return couldNotGetText

        val provider = sources.firstNotNullOfOrNull { it.provider }
            ?: return UIText.StringResource(R.string.error_source_gone) to true

        return UIText.StringResource(R.string.error_source_unavailable, provider) to false
    }

    private fun startSession() {
        if (tracker.isRunning) return
        if (_state.value.text.isEmpty()) return

        // A book being previewed from a file manager records nothing. Skimming a
        // book to decide whether to read it is not reading it, and a speed
        // measured over that skim would be a lie about the only thing a speed is
        // good for. Leaving the session unstarted is the whole guard: notes,
        // coverage and the end of a session are all already conditioned on one
        // being open.
        if (!_state.value.book.inLibrary) return

        tracker.start(overlayShowing = isOverlayShowing())
    }

    /** Whether something that earns no words is covering the text right now. */
    private fun isOverlayShowing(): Boolean = with(_state.value) {
        fullscreenImage != null ||
                drawer != null ||
                bottomSheet == ReaderScreen.SETTINGS_BOTTOM_SHEET ||
                // The search *bar*, not the mode: the mode outlives hiding the
                // menu, and reading a found passage with the bars away is
                // reading like any other.
                (search.active && showMenu)
    }

    /**
     * Starts the overlay clock again if something is *still* covering the text
     * after one of them closed — the search bar an image was opened over, say.
     * Called after the state has been updated, which is what tells the two
     * apart; [ReadingSessionTracker.overlayHidden] runs before it and has to
     * bank the span either way.
     */
    private fun resumeOverlayIfCovered() {
        if (isOverlayShowing()) tracker.overlayShown()
    }

    private suspend fun endSession() {
        val startTime = tracker.stop() ?: return
        val bookId = _state.value.book.id

        tracker.coverage(bookId, _state.value.book.scrollIndex)?.let {
            saveBookCoverageUseCase(it)
        }

        val session = recordReadingSessionUseCase(
            bookId = bookId,
            startTime = startTime,
            lastActiveTime = tracker.lastActive,
            endTime = System.currentTimeMillis(),
            wordsRead = tracker.wordsRead,
            overlayMs = tracker.overlayMs
        )

        // Only a session the statistics kept: one too short to count must not
        // quietly bump the book's totals either.
        if (session != null) {
            val book = _state.value.book
            updateReadBookUseCase(
                session = session,
                title = book.title,
                author = book.author.getAsString() ?: "",
                coveragePercent = tracker.coveragePercent,
                reachedEnd = tracker.reachedEnd
            )
        }

        tracker.closeLedger()
    }

    private suspend fun loadCoverage(text: List<ReaderText>) {
        // The one statistics step that does not hang off an open session, so it
        // needs the preview guard of its own; see [startSession].
        if (!_state.value.book.inLibrary) return

        // The one pass over every word of the book; the rest is applied here.
        val prefix = withContext(Dispatchers.Default) { text.wordPrefixSums() }
        val bookWords = prefix.last()
        val stored = getBookCoverageUseCase(
            bookId = _state.value.book.id,
            itemCount = text.size,
            bookWords = bookWords
        )

        tracker.loadCoverage(stored, itemCount = text.size, prefix = prefix)

        // Credit what is already on screen. The scroll flow emits its one
        // opening sample about 300 ms in and then nothing until something
        // moves, so on a book big enough for this load to lose that race the
        // first screen would never be credited at all — which is how a fresh
        // trilogy ended a whole session with empty coverage (device QA,
        // 2026-08-05).
        tracker.creditVisible(visibleIndices())
    }

    private class ImageSplit(
        val text: List<ReaderText>,
        val bytes: Map<String, ByteArray>,
        val imageSrcs: List<String>
    )

    /** [text] without its image bytes, the bytes by `src`, and every image's `src`. */
    private fun splitImageBytes(text: List<ReaderText>): ImageSplit {
        val bytes = HashMap<String, ByteArray>()
        val stripped = text.map { entry ->
            when {
                entry !is ReaderText.Image -> entry
                entry.image.bytes.isEmpty() -> entry
                else -> {
                    bytes[entry.image.src] = entry.image.bytes
                    entry.copy(image = entry.image.withoutBytes())
                }
            }
        }
        val srcs = stripped.filterIsInstance<ReaderText.Image>().map { it.image.src }
        return ImageSplit(stripped, bytes, srcs)
    }

    /**
     * Moves the list to a screen nobody is about to read (the slider, the
     * search arrows), so that its landing is not credited; see
     * [ReadingSessionTracker.jumped].
     */
    private fun jumpTo(index: Int, offset: Int) {
        val listState = _state.value.listState
        tracker.jumped(
            from = Checkpoint(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset
            ),
            to = Checkpoint(index = index, offset = offset)
        )
        listState.requestScrollToItem(index = index, scrollOffset = offset)
    }

    private fun visibleIndices(): List<Int> =
        _state.value.listState.layoutInfo.visibleItemsInfo.map { it.index }

    /**
     * Clears up after the reader of [bookId] — and does nothing if the model has
     * since moved on to another book.
     *
     * The check is the point. There is one model for the whole activity, and a
     * screen being replaced is disposed *after* its replacement has composed:
     * closing a book to open another one had the outgoing reader cancel the
     * incoming one's work, and the new book sat at its loading spinner forever.
     */
    fun clearAsync(bookId: Int) {
        if (_state.value.book.id != bookId) return
        viewModelScope.launch { clear() }
    }

    suspend fun clear() {
        // First, while the state still names the book it belongs to: [init]
        // clears before loading another one, and a session left running would
        // be recorded against the new book.
        endSession()

        searchJob?.cancel()
        coverageJob?.cancelAndJoin()
        tracker.reset()

        eventJobs.children.forEach { it.cancelAndJoin() }

        // Joined, not just cancelled: the image pass ends in blocking file I/O
        // that cancellation cannot interrupt, and it must not still be publishing
        // into the store that is emptied below.
        imageJob?.cancelAndJoin()

        // Releases every byte the reader held: the budgeted images in the store
        // and whatever a fresh parse was still waiting to hand over. The files
        // stay — dropping them is the next book's business
        // ([KeepOnlyBookImagesUseCase]), because this book may well be reopened.
        imageStore.reset()
        parsedImageBytes = emptyMap()

        _state.updateIfActive { ReaderState() }
    }

    @OptIn(FlowPreview::class)
    suspend fun updateProgress(listState: LazyListState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().debounce(300).collectLatest { (index, offset) ->
            tracker.settled(listState.layoutInfo.visibleItemsInfo.map { it.index })

            // Scrolled away from the match the counter is pinned to: "34 / 57"
            // would go on claiming a place the reader has left.
            _state.value.search.currentMatch?.let { match ->
                val stillOnScreen = listState.layoutInfo.visibleItemsInfo.any { item ->
                    item.index == match.itemIndex
                }
                if (!stillOnScreen) clearCurrentMatch()
            }

            if (
                _state.value.isLoading ||
                listState.layoutInfo.totalItemsCount == 0 ||
                _state.value.text.isEmpty() ||
                _state.value.errorMessage != null
            ) return@collectLatest

            val progress = calculateProgress(index)
            tracker.progressed(progress)

            val (currentChapter, currentChapterProgress) = getChapterProgressUseCase(
                index = index,
                text = _state.value.text
            )

            _state.updateIfActive {
                it.copy(
                    book = it.book.copy(
                        progress = progress,
                        scrollIndex = index,
                        scrollOffset = offset
                    ),
                    currentChapter = currentChapter,
                    currentChapterProgress = currentChapterProgress
                )
            }

            updateBookUseCase.position(_state.value.book.id, index, offset, progress)
        }
    }

    fun findChapterIndexAndLength(index: Int): Pair<Int, Int> {
        val (chapter, _) = getChapterProgressUseCase(index = index, text = _state.value.text)
        return chapter?.let { chapter ->
            val startIndex = _state.value.text
                .indexOf(chapter)
                .coerceIn(0, _state.value.text.lastIndex)
            val endIndex = (_state.value.text.indexOfFirst {
                it is Chapter && _state.value.text.indexOf(it) > startIndex
            }.takeIf { it != -1 }) ?: (_state.value.text.lastIndex + 1)

            val currentIndexInChapter = (index - startIndex).coerceAtLeast(1)
            val chapterLength = endIndex - (startIndex + 1)
            currentIndexInChapter to chapterLength
        } ?: (-1 to -1)
    }

    /**
     * The items on screen, which is what the search arrows step away from: a
     * match the reader is already looking at is not somewhere to go.
     */
    private fun visibleRange(): IntRange {
        val visible = _state.value.listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            val index = _state.value.listState.firstVisibleItemIndex
            return index..index
        }
        return visible.first().index..visible.last().index
    }

    /** Where a match lands when jumped to; see [SEARCH_LANDING_FRACTION]. */
    private fun searchLandingOffset(): Int {
        val info = _state.value.listState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        if (viewport <= 0) return 0
        return -(viewport * SEARCH_LANDING_FRACTION).toInt()
    }

    /**
     * Drops the match the counter is pinned to, without touching the query or
     * the results: after a jump the reader made some other way, "34 / 57" would
     * be pointing at a place they are no longer at.
     */
    private suspend fun clearCurrentMatch() {
        if (_state.value.search.current == -1) return
        _state.updateIfActive { it.copy(search = it.search.copy(current = -1)) }
    }

    private fun calculateProgress(firstVisibleItemIndex: Int? = null): Float {
        if (
            _state.value.isLoading ||
            _state.value.listState.layoutInfo.totalItemsCount == 0 ||
            _state.value.text.isEmpty() ||
            _state.value.errorMessage != null
        ) return _state.value.book.progress

        return readingProgress(
            firstVisibleIndex = firstVisibleItemIndex
                ?: _state.value.listState.firstVisibleItemIndex,
            lastVisibleIndex = _state.value.listState.layoutInfo.visibleItemsInfo
                .lastOrNull()?.index,
            lastIndex = _state.value.text.lastIndex
        )
    }

    /**
     * Sets the state from [function], unless the calling coroutine has been
     * cancelled. Not kotlinx's `update`: there is no compare-and-set loop,
     * because every caller runs on Main and nothing can write in between.
     *
     * The check is the point. [clear] cancels the previous book's events and
     * jobs, and one of them that has not reached its next suspension point yet
     * would otherwise write the old book's results into the new book's state.
     */
    private suspend inline fun <T> MutableStateFlow<T>.updateIfActive(function: (T) -> T) {
        currentCoroutineContext().ensureActive()
        value = function(value)
    }
}