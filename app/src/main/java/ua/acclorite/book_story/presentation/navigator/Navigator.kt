/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.navigator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = Navigator.Factory::class)
class Navigator @AssistedInject constructor(
    private val savedStateHandle: SavedStateHandle,
    @Assisted private val initialScreen: Screen
) : ViewModel() {

    val items = savedStateHandle.getStateFlow("items", mutableListOf(initialScreen))
    private fun StateFlow<MutableList<Screen>>.removeLast() {
        savedStateHandle["items"] = value.dropLast(1)
    }

    private fun StateFlow<MutableList<Screen>>.add(item: Screen) {
        savedStateHandle["items"] = value + item
    }

    val lastItem = items.map {
        it.lastOrNull() ?: initialScreen
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialScreen
    )

    /**
     * Whether anything is under the current screen — i.e. whether [pop] would
     * do something. A screen that can be reached both as the app's first one
     * and from somewhere else asks this to know which of the two it is.
     */
    val canPop: Boolean
        get() = items.value.count() > 1

    val lastEvent = savedStateHandle.getStateFlow("stack_event", StackEvent.DEFAULT)
    private fun changeStackEvent(stackEvent: StackEvent) {
        savedStateHandle["stack_event"] = stackEvent
    }


    fun push(
        targetScreen: Screen,
        popping: Boolean = false,
        saveInBackStack: Boolean = true
    ) {
        // Read from [items] rather than [lastItem]: the latter is a `map` on a
        // `stateIn`, so it lags a frame behind. A pop followed by a push in the
        // same frame — which is how one book replaces another — saw the screen
        // that had just been popped, and refused to push anything at all.
        if (items.value.lastOrNull()?.let { it::class == targetScreen::class } == true) return
        if (!saveInBackStack) items.removeLast()

        changeStackEvent(
            if (popping) StackEvent.POP
            else StackEvent.DEFAULT
        )

        if (lastItem.value::class == targetScreen::class) items.removeLast()
        items.add(targetScreen)
    }

    fun pop(popping: Boolean = true) {
        if (items.value.count() > 1) {
            changeStackEvent(
                if (popping) StackEvent.POP
                else StackEvent.DEFAULT
            )
            items.removeLast()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(startScreen: Screen): Navigator
    }
}