/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.helpers.coerceAndPreventNaN
import ua.acclorite.book_story.core.log.BookOpenTrace
import ua.acclorite.book_story.core.log.timed
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.domain.model.reader.BookImageStore
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderText.Chapter
import ua.acclorite.book_story.domain.model.reader.SEARCH_MIN_QUERY_LENGTH
import ua.acclorite.book_story.domain.model.reader.findSearchMatches
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.SessionWords
import ua.acclorite.book_story.domain.model.statistics.wordCount
import ua.acclorite.book_story.domain.model.statistics.wordPrefixSums
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
import ua.acclorite.book_story.presentation.history.HistoryScreen
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import ua.acclorite.book_story.presentation.reader.model.ReaderSearch
import ua.acclorite.book_story.presentation.reader.model.stepTarget
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * How long the typing has to stop before the book is scanned. Long enough that
 * an ordinary word is scanned once rather than once per letter.
 */
private const val SEARCH_DEBOUNCE = 300L

@HiltViewModel
class ReaderModel @Inject constructor(
    private val updateBookUseCase: UpdateBookUseCase,
    private val getTextUseCase: GetTextUseCase,
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

    private val mutex = Mutex()

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
    private val eventStack = mutableListOf<Job>()

    /** Start of the running reading session, or null when none is running. */
    private var sessionStartTime: Long? = null

    /**
     * When the reader last settled on a position. Only the idle *after* it is
     * capped, so a device left open on a page stops counting while a pause in
     * the middle of a sitting does not.
     */
    private var sessionLastActive: Long = 0L

    /**
     * What of the open book has been read — item indices, loaded with the text
     * and saved when the session ends. Held apart from [state] because it
     * changes on every settled scroll and nothing draws it.
     */
    private val coveredItems = mutableSetOf<Int>()
    private var coveredWords = 0
    private var coverageItemCount = 0
    private var coverageBookWords = 0
    private var coverageJob: Job? = null

    /**
     * Words before each item, built by the pass that counts the book. It is
     * what makes "left to read" a forecast: the bookmark's index reads straight
     * out of it, with no second walk over the text.
     */
    private var itemWordPrefix: IntArray? = null

    /**
     * Until the stored coverage has been read back, nothing is credited: the
     * set is about to be replaced by it.
     */
    private var coverageReady = false

    /** What this session has credited, de-duplicated; see [SessionWords]. */
    private val sessionWords = SessionWords()

    /**
     * The note the sheet is showing. Credited when it closes rather than when it
     * opens, so a mis-tap that is dismissed at once counts nothing.
     */
    private var openNoteId: String? = null

    /**
     * How long the text has been covered this session by something that earns
     * no words, and when the covering started. The session's interval is left
     * alone — this is subtracted only where a speed is divided.
     *
     * The note sheet does not count: [creditOpenNote] credits its words, so its
     * time belongs in that denominator.
     */
    private var sessionOverlayMs = 0L
    private var overlayShownAt: Long? = null

    /**
     * Set while dragging the progress slider, whose landings are screens nobody
     * read — it stops wherever the drag pauses.
     *
     * Deliberately *not* set for the other ways the reader moves the list
     * itself. Restoring the bookmark, opening a chapter and returning to a
     * checkpoint all land where the reading is about to happen, and that landing
     * is the only sample that screen will ever get: suppressing it left the
     * first screen of every book uncredited, so reading one cover to cover
     * stopped short of 100 % (found in device QA, 2026-08-05).
     */
    private var landingAfterJump = false

    /**
     * Whether this session got to the end of the book. Carried to the book's
     * record when the session is written, rather than set the moment it
     * happens: there may be no record yet to set it on.
     */
    private var reachedEnd = false

    fun onEvent(event: ReaderEvent) {
        viewModelScope.launch {
            when (event) {
                is ReaderEvent.OnLoadText -> {
                    withContext(Dispatchers.Default) {
                        val parsedText = getTextUseCase(_state.value.book.id)
                        val text = parsedText.text
                        BookOpenTrace.mark("text parsed")
                        ensureActive()

                        if (text.isEmpty()) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = UIText.StringResource(
                                        resId = R.string.error_could_not_get_text
                                    )
                                )
                            }
                            _effects.emit(ReaderEffect.OnSystemBarsVisibility(show = true))
                            return@withContext
                        }

                        _effects.emit(ReaderEffect.OnSystemBarsVisibility(show = null))

                        // A freshly parsed book carries its image bytes, one
                        // restored from the cache carries metadata only. Either
                        // way the bytes are kept out of the state: [OnLoadImages]
                        // hands them to the store in the background, which holds
                        // a bounded few and puts the rest on disk. Holding them
                        // here as well would pin tens of megabytes for the whole
                        // session.
                        val bytes = HashMap<String, ByteArray>()
                        val strippedText = text.map { entry ->
                            when {
                                entry !is ReaderText.Image -> entry
                                entry.image.bytes.isEmpty() -> entry
                                else -> {
                                    bytes[entry.image.src] = entry.image.bytes
                                    entry.copy(image = entry.image.withoutBytes())
                                }
                            }
                        }
                        parsedImageBytes = bytes
                        imageStore.reset(
                            strippedText.filterIsInstance<ReaderText.Image>()
                                .map { it.image.src }
                        )

                        val lastOpened = getHistoryForBookUseCase(_state.value.book.id)?.time
                        _state.update {
                            it.copy(
                                showMenu = false,
                                book = it.book.copy(
                                    lastOpened = lastOpened
                                ),
                                text = strippedText,
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
                        coverageJob = viewModelScope.launch(Dispatchers.Default) {
                            loadCoverage(strippedText)
                        }

                        updateBookUseCase(_state.value.book)

                        LibraryScreen.refreshListChannel.trySend(0)
                        HistoryScreen.refreshListChannel.trySend(0)

                        onEvent(ReaderEvent.OnRestoreScroll)
                    }
                }

                is ReaderEvent.OnLoadImages -> {
                    if (imageJob?.isActive == true) return@launch
                    val pending = imageStore.pending()
                    if (pending.isEmpty()) return@launch

                    val bookId = _state.value.book.id
                    val parsed = parsedImageBytes
                    imageJob = viewModelScope.launch(Dispatchers.IO) {
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

                    _state.update {
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
                    withContext(Dispatchers.Default) {
                        if (_state.value.lockMenu) return@withContext

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

                            _state.update {
                                it.copy(checkpoints = checkpoints)
                            }
                        }

                        _state.update {
                            it.copy(showMenu = event.show)
                        }

                        // With the search open, the bars going away is the
                        // reader turning back to the text — and coming back is
                        // the search covering it again.
                        if (_state.value.search.active) {
                            if (event.show) overlayShown() else overlayHidden()
                        }
                    }
                }

                is ReaderEvent.OnChangeProgress -> {
                    withContext(Dispatchers.Default) {
                        _state.update {
                            it.copy(
                                book = it.book.copy(
                                    progress = event.progress,
                                    scrollIndex = event.firstVisibleItemIndex,
                                    scrollOffset = event.firstVisibleItemOffset
                                )
                            )
                        }

                        updateBookUseCase(_state.value.book)

                        LibraryScreen.refreshListChannel.trySend(300)
                        HistoryScreen.refreshListChannel.trySend(300)
                    }
                }

                is ReaderEvent.OnUpdateChapter -> {
                    _state.update {
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
                    withContext(Dispatchers.Default) {
                        val chapterIndex = _state.value.text
                            .indexOf(event.chapter)
                            .takeIf { it != -1 }
                        if (chapterIndex == null) return@withContext

                        clearCurrentMatch()
                        _state.value.listState.requestScrollToItem(
                            index = chapterIndex,
                            scrollOffset = 0
                        )

                        onEvent(ReaderEvent.OnUpdateChapter(chapterIndex))
                        onEvent(
                            ReaderEvent.OnChangeProgress(
                                progress = calculateProgress(chapterIndex),
                                firstVisibleItemIndex = chapterIndex,
                                firstVisibleItemOffset = 0
                            )
                        )
                    }
                }

                is ReaderEvent.OnScroll -> {
                    scrollJob?.cancel()
                    scrollJob = viewModelScope.launch(Dispatchers.IO) {
                        delay(300)

                        val scrollTo = (_state.value.text.lastIndex * event.progress).roundToInt()

                        clearCurrentMatch()
                        jumpedToPosition()
                        _state.value.listState.requestScrollToItem(
                            index = scrollTo,
                            scrollOffset = 0
                        )

                        onEvent(ReaderEvent.OnUpdateChapter(scrollTo))
                    }
                }

                is ReaderEvent.OnRestoreCheckpoint -> {
                    withContext(Dispatchers.Default) {
                        _state.update {
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

                        onEvent(ReaderEvent.OnUpdateChapter(event.checkpoint.index))
                        onEvent(
                            ReaderEvent.OnChangeProgress(
                                progress = calculateProgress(event.checkpoint.index),
                                firstVisibleItemIndex = event.checkpoint.index,
                                firstVisibleItemOffset = event.checkpoint.offset,
                            )
                        )
                    }
                }

                is ReaderEvent.OnSearchVisibility -> {
                    searchJob?.cancel()

                    when (event.show) {
                        true -> {
                            creditOpenNote()
                            overlayShown()
                            _state.update {
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
                            overlayHidden()
                            _state.update { it.copy(search = ReaderSearch()) }
                        }
                    }
                }

                is ReaderEvent.OnSearchQueryChange -> {
                    searchJob?.cancel()

                    val query = event.query
                    val searchable = query.trim().length >= SEARCH_MIN_QUERY_LENGTH
                    _state.update {
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

                    searchJob = viewModelScope.launch(Dispatchers.Default) {
                        delay(SEARCH_DEBOUNCE)

                        val matches = _state.value.text.findSearchMatches(
                            query = query,
                            notes = _state.value.notes
                        )
                        ensureActive()

                        _state.update {
                            // A query that moved on while this scan ran owns the
                            // bar now; these matches are for a string nobody typed.
                            if (it.search.query != query) return@update it
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
                    withContext(Dispatchers.Default) {
                        val target = _state.value.search.stepTarget(
                            forward = event.forward,
                            visible = visibleRange()
                        ) ?: return@withContext
                        val match = _state.value.search.matches[target]

                        _state.update {
                            it.copy(search = it.search.copy(current = target))
                        }

                        // Stepping through matches lands on screens nobody reads,
                        // exactly like dragging the slider does.
                        jumpedToPosition()
                        _state.value.listState.requestScrollToItem(
                            index = match.itemIndex,
                            scrollOffset = 0
                        )
                        // No [OnChangeProgress] on purpose: searching must not
                        // move the position the book is reopened at.
                        onEvent(ReaderEvent.OnUpdateChapter(match.itemIndex))
                    }
                }

                is ReaderEvent.OnSearchReturn -> {
                    searchJob?.cancel()

                    val origin = _state.value.search.origin
                    overlayHidden()
                    _state.update { it.copy(search = ReaderSearch()) }

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
                    _state.update {
                        it.copy(
                            lockMenu = true
                        )
                    }

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
                        _state.update {
                            it.copy(
                                book = it.book.copy(
                                    progress = calculateProgress(),
                                    scrollIndex = _state.value.listState.firstVisibleItemIndex,
                                    scrollOffset = _state.value.listState.firstVisibleItemScrollOffset
                                )
                            )
                        }

                        updateBookUseCase(_state.value.book)

                        LibraryScreen.refreshListChannel.trySend(0)
                        HistoryScreen.refreshListChannel.trySend(0)
                    }

                    // Leaving a book that was only being previewed is the user
                    // declining it. Nothing about it is kept — the row, the
                    // cover and the image files all go, and the statistics never
                    // held anything to begin with.
                    discardPreviewsUseCase.discard(_state.value.book)

                    _effects.emit(
                        ReaderEffect.OnSystemBarsVisibility(
                            show = true
                        )
                    )
                    _effects.emit(ReaderEffect.OnResetBrightness)
                    event.navigate()
                }

                is ReaderEvent.OnAddToLibrary -> {
                    addToLibrary()
                }

                is ReaderEvent.OnGrantFolder -> {
                    grantPersistableUriPermissionUseCase(event.uri)
                    addToLibrary()
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

                is ReaderEvent.OnOpenDictionary -> {
                    _effects.emit(
                        ReaderEffect.OnOpenDictionary(
                            textToDefine = event.textToDefine
                        )
                    )
                }

                is ReaderEvent.OnShowSettingsBottomSheet -> {
                    creditOpenNote()
                    overlayShown()
                    _state.update {
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
                    overlayHidden()
                    openNoteId = id
                    _state.update {
                        it.copy(
                            bottomSheet = ReaderScreen.NOTE_BOTTOM_SHEET,
                            currentNote = note,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnOpenImage -> {
                    creditOpenNote()
                    overlayShown()
                    _state.update {
                        it.copy(
                            fullscreenImage = event.image,
                            bottomSheet = null,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnDismissImage -> {
                    overlayHidden()
                    _state.update {
                        it.copy(
                            fullscreenImage = null
                        )
                    }
                    // What it was opened over may still be covering the text.
                    resumeOverlayIfCovered()
                }

                is ReaderEvent.OnDismissBottomSheet -> {
                    creditOpenNote()
                    overlayHidden()
                    _state.update {
                        it.copy(
                            bottomSheet = null
                        )
                    }
                    resumeOverlayIfCovered()
                }

                is ReaderEvent.OnShowChaptersDrawer -> {
                    creditOpenNote()
                    overlayShown()
                    _state.update {
                        it.copy(
                            drawer = ReaderScreen.CHAPTERS_DRAWER,
                            bottomSheet = null
                        )
                    }
                }

                is ReaderEvent.OnDismissDrawer -> {
                    overlayHidden()
                    _state.update {
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
        }.also { eventStack.add(it) }
    }

    fun init(bookId: Int) {
        viewModelScope.launch(Dispatchers.Default) {
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

            _state.update {
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
                // may have moved while the copy was being made, and [updateProgress]
                // writes this row whole on every settled scroll — a stale path or
                // preview URI here would be written straight back over the row
                // that was just promoted, leaving the book unopenable.
                _state.update {
                    it.copy(
                        book = it.book.copy(
                            inLibrary = true,
                            previewUri = null,
                            filePath = result.book.filePath
                        )
                    )
                }

                startSession()
                coverageJob = viewModelScope.launch(Dispatchers.Default) {
                    loadCoverage(_state.value.text)
                }

                LibraryScreen.refreshListChannel.trySend(0)
                HistoryScreen.refreshListChannel.trySend(0)
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

    private fun startSession() {
        if (sessionStartTime != null) return
        if (_state.value.text.isEmpty()) return

        // A book being previewed from a file manager records nothing. Skimming a
        // book to decide whether to read it is not reading it, and a speed
        // measured over that skim would be a lie about the only thing a speed is
        // good for. Leaving the session unstarted is the whole guard: notes,
        // coverage and the end of a session are all already conditioned on one
        // being open.
        if (!_state.value.book.inLibrary) return

        val now = System.currentTimeMillis()
        sessionStartTime = now
        sessionLastActive = now
        sessionOverlayMs = 0

        // Coming back to a text that is still covered — stopped from inside the
        // image viewer, say. The span the stop closed out resumes here, or the
        // whole of it would be counted as reading.
        overlayShownAt = if (isOverlayShowing()) now else null
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

    private fun overlayShown() {
        markActive()
        if (overlayShownAt == null) overlayShownAt = System.currentTimeMillis()
    }

    /**
     * Starts the overlay clock again if something is *still* covering the text
     * after one of them closed — the search bar an image was opened over, say.
     * Called after the state has been updated, which is what tells the two
     * apart; [overlayHidden] runs before it and has to bank the span either way.
     */
    private fun resumeOverlayIfCovered() {
        if (isOverlayShowing()) overlayShown()
    }

    private fun overlayHidden() {
        markActive()
        accrueOverlay()
    }

    /**
     * Banks a running overlay span, without touching [sessionLastActive]. The
     * end of a session goes through here rather than [overlayHidden]: marking
     * the reader active at that moment would move the last sign of life to
     * *now* and so lift the idle cap off every session that ends.
     */
    private fun accrueOverlay() {
        val shownAt = overlayShownAt ?: return
        overlayShownAt = null
        sessionOverlayMs += (System.currentTimeMillis() - shownAt).coerceAtLeast(0)
    }

    private suspend fun endSession() {
        val startTime = sessionStartTime ?: return

        // A note still open when the reader is stopped was read like any other:
        // credit it before the session's words are written. An overlay still up
        // is closed out for the same reason — being stopped from inside the
        // image viewer must not lose the time it was holding.
        creditOpenNote()
        accrueOverlay()
        sessionStartTime = null

        if (coverageReady) {
            saveBookCoverageUseCase(
                ReadingCoverage(
                    bookId = _state.value.book.id,
                    itemCount = coverageItemCount,
                    bookWords = coverageBookWords,
                    covered = coveredItems.toSet(),
                    coveredWords = coveredWords,
                    wordsBeforeBookmark = wordsBeforeBookmark()
                )
            )
        }

        val session = recordReadingSessionUseCase(
            bookId = _state.value.book.id,
            startTime = startTime,
            lastActiveTime = sessionLastActive,
            endTime = System.currentTimeMillis(),
            wordsRead = sessionWords.total,
            overlayMs = sessionOverlayMs
        )

        // Only a session the statistics kept: one too short to count must not
        // quietly bump the book's totals either.
        if (session != null) {
            val book = _state.value.book
            updateReadBookUseCase(
                session = session,
                title = book.title,
                author = book.author.getAsString() ?: "",
                coveragePercent = if (coverageItemCount <= 0) 0f
                else coveredItems.size.toFloat() / coverageItemCount,
                reachedEnd = reachedEnd
            )
        }

        sessionWords.clear()
        reachedEnd = false
    }

    /**
     * Something that is not a scroll but is still the reader at work — opening
     * or closing a note. Without it the idle cap would measure from the last
     * scroll, and ten minutes spent in a long note would be cut off the end of
     * the session as if the device had been put down.
     */
    private fun markActive() {
        sessionLastActive = System.currentTimeMillis()
    }

    /**
     * Credits the note the sheet was showing, if any.
     *
     * Note words are **volume only**: notes are not items, so they enter neither
     * [coveredItems] nor [coverageBookWords]. Putting them in the book's total
     * would mean no book could ever reach 100 % coverage without opening every
     * note; leaving them out of the session's words leaves the time counted and
     * the words not, which drags every speed figure down.
     */
    private fun creditOpenNote() {
        val id = openNoteId ?: return
        openNoteId = null

        if (sessionStartTime == null) return
        sessionWords.creditNote(id, _state.value.notes[id]?.wordCount() ?: 0)
    }

    private suspend fun loadCoverage(text: List<ReaderText>) {
        // The one statistics step that does not hang off an open session, so it
        // needs the preview guard of its own; see [startSession].
        if (!_state.value.book.inLibrary) return

        val prefix = text.wordPrefixSums()
        val bookWords = prefix.last()
        val stored = getBookCoverageUseCase(
            bookId = _state.value.book.id,
            itemCount = text.size,
            bookWords = bookWords
        )

        coverageItemCount = text.size
        coverageBookWords = bookWords
        itemWordPrefix = prefix
        coveredItems.clear()
        coveredItems.addAll(stored.covered)
        coveredWords = stored.coveredWords
        coverageReady = true

        // Credit what is already on screen. The scroll flow emits its one
        // opening sample about 300 ms in and then nothing until something
        // moves, so on a book big enough for this load to lose that race the
        // first screen would never be credited at all — which is how a fresh
        // trilogy ended a whole session with empty coverage (device QA,
        // 2026-08-05). On Main, where every other credit happens.
        withContext(Dispatchers.Main) {
            creditVisible(_state.value.listState.layoutInfo.visibleItemsInfo)
        }
    }

    /** The reader is about to move the list itself; see [landingAfterJump]. */
    private fun jumpedToPosition() {
        landingAfterJump = true
    }

    /**
     * Marks what is on screen as read. Called only for *settled* positions, so
     * a fling across half the book credits nothing it flew past: the flow never
     * emits those in between.
     */
    private fun creditVisible(visible: List<LazyListItemInfo>) {
        // Nothing is credited outside a session, the way a note is not: the
        // coverage pass finishes on its own thread and can land after the
        // session that started it has been banked and its ledger cleared — a
        // book opened and left at once would then pay its last screen into the
        // *next* book's first session.
        if (sessionStartTime == null) return

        if (landingAfterJump) {
            landingAfterJump = false
            return
        }
        if (!coverageReady) return

        val text = _state.value.text
        for (item in visible) {
            val entry = text.getOrNull(item.index) ?: continue
            val words = entry.wordCount()

            sessionWords.creditItem(item.index, words)
            if (coveredItems.add(item.index)) coveredWords += words
        }
    }

    /**
     * Words before the bookmark, as the session leaves it. Recorded here rather
     * than on every settled scroll because the coverage row is written here
     * anyway, and the position at the end of a session is the one being
     * described.
     *
     * The index is clamped only to keep the lookup in range; a text that has
     * been reparsed under the bookmark is caught properly by the coverage row's
     * item count.
     */
    private fun wordsBeforeBookmark(): Int? {
        val prefix = itemWordPrefix ?: return null
        return prefix[_state.value.book.scrollIndex.coerceIn(0, prefix.lastIndex)]
    }

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
        coverageReady = false
        coveredItems.clear()
        coveredWords = 0
        coverageItemCount = 0
        coverageBookWords = 0
        itemWordPrefix = null
        landingAfterJump = false
        openNoteId = null
        sessionOverlayMs = 0
        overlayShownAt = null

        eventStack.forEach { job ->
            job.cancel()
            job.join()
        }
        eventStack.clear()

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

        _state.update { ReaderState() }
    }

    @OptIn(FlowPreview::class)
    suspend fun updateProgress(listState: LazyListState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().debounce(300).collectLatest { (index, offset) ->
            // Every settled position is a sign of life, even one the guard below
            // discards: the reader is being scrolled either way.
            sessionLastActive = System.currentTimeMillis()
            creditVisible(listState.layoutInfo.visibleItemsInfo)

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
            if (progress >= 1f) reachedEnd = true

            val (currentChapter, currentChapterProgress) = getChapterProgressUseCase(
                index = index,
                text = _state.value.text
            )

            _state.update {
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

            updateBookUseCase(_state.value.book)

            LibraryScreen.refreshListChannel.trySend(0)
            HistoryScreen.refreshListChannel.trySend(0)
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

    /**
     * Drops the match the counter is pinned to, without touching the query or
     * the results: after a jump the reader made some other way, "34 / 57" would
     * be pointing at a place they are no longer at.
     */
    private suspend fun clearCurrentMatch() {
        if (_state.value.search.current == -1) return
        _state.update { it.copy(search = it.search.copy(current = -1)) }
    }

    private fun calculateProgress(firstVisibleItemIndex: Int? = null): Float {
        if (
            _state.value.isLoading ||
            _state.value.listState.layoutInfo.totalItemsCount == 0 ||
            _state.value.text.isEmpty() ||
            _state.value.errorMessage != null
        ) return _state.value.book.progress

        if ((firstVisibleItemIndex ?: _state.value.listState.firstVisibleItemIndex) == 0) return 0f

        val lastVisibleItemIndex = _state.value.listState.layoutInfo.visibleItemsInfo.last().index
        if (lastVisibleItemIndex >= _state.value.text.lastIndex) return 1f

        return (firstVisibleItemIndex ?: _state.value.listState.firstVisibleItemIndex)
            .div(_state.value.text.lastIndex.toFloat())
            .coerceAndPreventNaN()
    }

    private suspend inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
        mutex.withLock {
            coroutineContext.ensureActive()
            this.value = function(this.value)
        }
    }
}