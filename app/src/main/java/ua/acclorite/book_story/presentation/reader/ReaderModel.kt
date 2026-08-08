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
import ua.acclorite.book_story.domain.model.statistics.ReadingCoverage
import ua.acclorite.book_story.domain.model.statistics.wordCount
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import ua.acclorite.book_story.domain.use_case.book.GetChapterProgressUseCase
import ua.acclorite.book_story.domain.use_case.book.GetTextUseCase
import ua.acclorite.book_story.domain.use_case.book.KeepOnlyBookImagesUseCase
import ua.acclorite.book_story.domain.use_case.book.LoadBookImagesUseCase
import ua.acclorite.book_story.domain.use_case.book.UpdateBookUseCase
import ua.acclorite.book_story.domain.use_case.history.GetHistoryForBookUseCase
import ua.acclorite.book_story.domain.use_case.statistics.GetBookCoverageUseCase
import ua.acclorite.book_story.domain.use_case.statistics.RecordReadingSessionUseCase
import ua.acclorite.book_story.domain.use_case.statistics.SaveBookCoverageUseCase
import ua.acclorite.book_story.presentation.history.HistoryScreen
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.reader.model.Checkpoint
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

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
    private val saveBookCoverageUseCase: SaveBookCoverageUseCase
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
     * Until the stored coverage has been read back, nothing is credited: the
     * set is about to be replaced by it.
     */
    private var coverageReady = false

    /** Items credited this session, so a screen re-read now counts once. */
    private val sessionSeen = mutableSetOf<Int>()
    private var sessionWords = 0

    /**
     * Set when the reader moves the list itself. The settled position that
     * follows is where a jump landed, not something that was read — otherwise
     * dragging the progress slider would credit every screen it paused on.
     */
    private var landingAfterJump = false

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

                    jumpedToPosition()
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

                        jumpedToPosition()
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

                        jumpedToPosition()
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

                    _effects.emit(
                        ReaderEffect.OnSystemBarsVisibility(
                            show = true
                        )
                    )
                    _effects.emit(ReaderEffect.OnResetBrightness)
                    event.navigate()
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

                    _state.update {
                        it.copy(
                            bottomSheet = ReaderScreen.NOTE_BOTTOM_SHEET,
                            currentNote = note,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnOpenImage -> {
                    _state.update {
                        it.copy(
                            fullscreenImage = event.image,
                            bottomSheet = null,
                            drawer = null
                        )
                    }
                }

                is ReaderEvent.OnDismissImage -> {
                    _state.update {
                        it.copy(
                            fullscreenImage = null
                        )
                    }
                }

                is ReaderEvent.OnDismissBottomSheet -> {
                    _state.update {
                        it.copy(
                            bottomSheet = null
                        )
                    }
                }

                is ReaderEvent.OnShowChaptersDrawer -> {
                    _state.update {
                        it.copy(
                            drawer = ReaderScreen.CHAPTERS_DRAWER,
                            bottomSheet = null
                        )
                    }
                }

                is ReaderEvent.OnDismissDrawer -> {
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

    private fun startSession() {
        if (sessionStartTime != null) return
        if (_state.value.text.isEmpty()) return

        val now = System.currentTimeMillis()
        sessionStartTime = now
        sessionLastActive = now
    }

    private suspend fun endSession() {
        val startTime = sessionStartTime ?: return
        sessionStartTime = null

        if (coverageReady) {
            saveBookCoverageUseCase(
                ReadingCoverage(
                    bookId = _state.value.book.id,
                    itemCount = coverageItemCount,
                    bookWords = coverageBookWords,
                    covered = coveredItems.toSet(),
                    coveredWords = coveredWords
                )
            )
        }

        recordReadingSessionUseCase(
            bookId = _state.value.book.id,
            startTime = startTime,
            lastActiveTime = sessionLastActive,
            endTime = System.currentTimeMillis(),
            wordsRead = sessionWords
        )

        sessionSeen.clear()
        sessionWords = 0
    }

    private suspend fun loadCoverage(text: List<ReaderText>) {
        val bookWords = text.sumOf { it.wordCount() }
        val stored = getBookCoverageUseCase(
            bookId = _state.value.book.id,
            itemCount = text.size,
            bookWords = bookWords
        )

        coverageItemCount = text.size
        coverageBookWords = bookWords
        coveredItems.clear()
        coveredItems.addAll(stored.covered)
        coveredWords = stored.coveredWords
        coverageReady = true
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
        if (landingAfterJump) {
            landingAfterJump = false
            return
        }
        if (!coverageReady) return

        val text = _state.value.text
        for (item in visible) {
            val entry = text.getOrNull(item.index) ?: continue
            val words = entry.wordCount()

            if (sessionSeen.add(item.index)) sessionWords += words
            if (coveredItems.add(item.index)) coveredWords += words
        }
    }

    fun clearAsync() {
        viewModelScope.launch { clear() }
    }

    suspend fun clear() {
        // First, while the state still names the book it belongs to: [init]
        // clears before loading another one, and a session left running would
        // be recorded against the new book.
        endSession()

        coverageJob?.cancelAndJoin()
        coverageReady = false
        coveredItems.clear()
        coveredWords = 0
        coverageItemCount = 0
        coverageBookWords = 0
        landingAfterJump = false

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

            if (
                _state.value.isLoading ||
                listState.layoutInfo.totalItemsCount == 0 ||
                _state.value.text.isEmpty() ||
                _state.value.errorMessage != null
            ) return@collectLatest

            val progress = calculateProgress(index)
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