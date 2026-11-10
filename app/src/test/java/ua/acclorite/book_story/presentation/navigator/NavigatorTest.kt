/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.navigator

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The back stack's rules. Robolectric only for the main looper that the
 * navigator's `viewModelScope` needs.
 */
@RunWith(RobolectricTestRunner::class)
class NavigatorTest {

    private data object Home : Screen {
        @Composable
        override fun Content() = Unit
    }

    private data class Info(val id: Int) : Screen {
        @Composable
        override fun Content() = Unit
    }

    private data class Reader(val id: Int) : Screen {
        @Composable
        override fun Content() = Unit
    }

    private fun navigator(vararg stack: Screen): Navigator =
        Navigator(SavedStateHandle(), Home).apply { stack.forEach { push(it) } }

    @Test
    fun pushAddsOnTop() {
        val navigator = navigator(Info(1))

        assertEquals(listOf(Home, Info(1)), navigator.items.value)
    }

    @Test
    fun pushOfTheSameKindAsTheTopIsIgnored() {
        val navigator = navigator(Info(1))

        navigator.push(Info(2))

        assertEquals(listOf(Home, Info(1)), navigator.items.value)
    }

    @Test
    fun aScreenReplacingItselfDoesNotStackTwoOfAKind() {
        // Book info → reader → "book info" from the reader, which replaces the
        // reader: the book info underneath goes too, rather than staying under
        // a second one.
        val navigator = navigator(Info(1), Reader(1))

        navigator.push(Info(1), saveInBackStack = false)

        assertEquals(listOf(Home, Info(1)), navigator.items.value)
    }

    @Test
    fun popRightAfterAPushSeesThePush() {
        // Both in one go, before anything had a chance to run: the order the
        // stack ends up in must not depend on a collector catching up.
        val navigator = navigator(Info(1))

        navigator.pop()
        navigator.push(Reader(1))

        assertEquals(listOf(Home, Reader(1)), navigator.items.value)
    }

    @Test
    fun popNeverDropsTheLastScreen() {
        val navigator = navigator()

        navigator.pop()

        assertEquals(listOf(Home), navigator.items.value)
    }

    @Test
    fun popFromTheTopScreenPops() {
        val navigator = navigator(Info(1), Reader(1))

        navigator.pop(from = Reader(1))

        assertEquals(listOf(Home, Info(1)), navigator.items.value)
    }

    @Test
    fun aLatePopFromAScreenAlreadyGoneDoesNothing() {
        // The reader asked to leave twice (a double Back): the second request
        // must not take book info with it.
        val navigator = navigator(Info(1), Reader(1))

        navigator.pop(from = Reader(1))
        navigator.pop(from = Reader(1))

        assertEquals(listOf(Home, Info(1)), navigator.items.value)
    }

    @Test
    fun popFromAnotherInstanceOfTheTopKindDoesNothing() {
        val navigator = navigator(Reader(2))

        navigator.pop(from = Reader(1))

        assertEquals(listOf(Home, Reader(2)), navigator.items.value)
    }
}
