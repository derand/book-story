/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.acclorite.book_story.domain.model.file.BookSource
import ua.acclorite.book_story.domain.use_case.file_system.GetBookSourcesUseCase
import ua.acclorite.book_story.domain.use_case.permission.GrantPersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.permission.ReleasePersistableUriPermissionUseCase
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import javax.inject.Inject

/** The folders books are read from (Browse settings): grant, release, check. */
@HiltViewModel
class BookSourcesModel @Inject constructor(
    private val grantPersistableUriPermissionUseCase: GrantPersistableUriPermissionUseCase,
    private val releasePersistableUriPermissionUseCase: ReleasePersistableUriPermissionUseCase,
    private val getBookSourcesUseCase: GetBookSourcesUseCase
) : ViewModel() {

    /**
     * The places books are read from, each with whether it answered when it was
     * last asked. Read when the Browse settings open and after any grant
     * changes; a source going quiet in between is what the next open reports.
     */
    private val _bookSources = MutableStateFlow<List<BookSource>>(emptyList())
    val bookSources = _bookSources.asStateFlow()

    private val _effects = MutableSharedFlow<BookSourcesEffect>()
    val effects = _effects.asSharedFlow()

    /**
     * Held across reading the sources *and* storing them, so two refreshes
     * cannot interleave.
     *
     * `onEvent` launches a coroutine per event and nothing orders them, so
     * removing two folders quickly used to let the earlier refresh finish last
     * and put a row back that was already gone — with a Clear button pointing at
     * a grant that no longer existed.
     */
    private val bookSourcesMutex = Mutex()

    fun onEvent(event: BookSourcesEvent) {
        viewModelScope.launch {
            when (event) {
                is BookSourcesEvent.OnGrantPersistableUriPermission -> sourcesChanged(
                    changed = grantPersistableUriPermissionUseCase(event.uri),
                    added = true
                )

                is BookSourcesEvent.OnReleasePersistableUriPermission -> sourcesChanged(
                    changed = releasePersistableUriPermissionUseCase(event.uri),
                    added = false
                )

                is BookSourcesEvent.OnRefreshBookSources -> refreshBookSources()
            }
        }
    }

    /**
     * After a grant was taken or released: Browse walks again only once the
     * grant has changed, so the walk sees the new set of folders, and a failure
     * is said rather than leaving the list silently as it was.
     */
    private suspend fun sourcesChanged(changed: Boolean, added: Boolean) {
        if (changed) {
            BrowseScreen.refreshListChannel.trySend(Unit)
        } else {
            _effects.emit(BookSourcesEffect.OnSourceChangeFailed(added = added))
        }
        refreshBookSources()
    }

    private suspend fun refreshBookSources() = bookSourcesMutex.withLock {
        _bookSources.value = getBookSourcesUseCase()
    }
}
