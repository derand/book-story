/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ua.acclorite.book_story.domain.use_case.book.DiscardPreviewsUseCase
import ua.acclorite.book_story.domain.use_case.book.OpenBookFromUriUseCase
import javax.inject.Inject

/**
 * Books arriving from outside the app: a file tapped in a file manager, handed
 * over as `ACTION_VIEW`.
 *
 * Held by the activity rather than a screen because the file arrives before any
 * screen does, and because the answer must survive the file being parsed, which
 * on a book this app has never seen is seconds rather than milliseconds.
 */
@HiltViewModel
class MainModel @Inject constructor(
    private val openBookFromUriUseCase: OpenBookFromUriUseCase,
    private val discardPreviewsUseCase: DiscardPreviewsUseCase
) : ViewModel() {

    sealed interface FileOpen {
        data class Open(val bookId: Int) : FileOpen
        data class Failed(val reason: OpenBookFromUriUseCase.Result) : FileOpen
    }

    /**
     * A channel rather than a state: the outcome must be acted on exactly once,
     * and it must survive arriving before anything is listening. The activity's
     * intent is read in `onCreate`, before the first composition, and during a
     * screen transition two compositions collect at the same moment — a replayed
     * state would open the book twice.
     */
    private val _fileOpen = Channel<FileOpen>(Channel.BUFFERED)
    val fileOpen = _fileOpen.receiveAsFlow()

    private val _openingFile = MutableStateFlow(false)
    val openingFile = _openingFile.asStateFlow()

    /**
     * App start is the only reliable place to collect previews left by a killed
     * process, since nothing runs on the way out of one. Held as a job because a
     * file arriving in the same breath must not have its fresh preview swept by
     * a pass that started before it existed.
     */
    private val sweep: Job = viewModelScope.launch { discardPreviewsUseCase.sweep() }

    fun onFileReceived(uri: String) {
        if (_openingFile.value) return

        _openingFile.value = true
        viewModelScope.launch {
            sweep.join()
            try {
                val result = openBookFromUriUseCase(uri)
                _fileOpen.send(
                    when (result) {
                        is OpenBookFromUriUseCase.Result.Open -> FileOpen.Open(result.bookId)
                        else -> FileOpen.Failed(result)
                    }
                )
            } finally {
                _openingFile.value = false
            }
        }
    }
}
