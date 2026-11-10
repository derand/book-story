/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.navigator

import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.Dispatchers
import ua.acclorite.book_story.presentation.navigator.Navigator
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.navigator.StackEvent
import ua.acclorite.book_story.ui.common.helpers.LocalActivity

@Composable
fun rememberNavigator(initialScreen: Screen): Navigator {
    val activity = LocalActivity.current
    return activity.viewModels<Navigator>(
        extrasProducer = {
            activity.defaultViewModelCreationExtras
                .withCreationCallback<Navigator.Factory> { factory ->
                    factory.create(initialScreen)
                }
        }
    ).value
}

@Composable
fun Navigator(
    modifier: Modifier = Modifier,
    initialScreen: Screen,
    transitionSpec: AnimatedContentTransitionScope<Screen>.(lastEvent: StackEvent) -> ContentTransform,
    contentKey: (Screen) -> Any? = { it },
    backHandlerEnabled: (Screen) -> Boolean = { true },
    /**
     * Drawn over every screen and composed once, outside the animation.
     *
     * For work that has to outlive a navigation: anything inside [content] is
     * disposed the moment its screen is replaced, taking its effects with it, so
     * a job that navigates cannot live there — it would cancel itself halfway.
     */
    overlay: @Composable () -> Unit = {},
    content: @Composable (currentScreen: Screen) -> Unit
) {
    val navigator = rememberNavigator(initialScreen = initialScreen)
    val currentScreen = navigator.lastItem.collectAsStateWithLifecycle(
        context = Dispatchers.Main.immediate
    )
    val lastEvent = navigator.lastEvent.collectAsStateWithLifecycle(
        context = Dispatchers.Main.immediate
    )
    val stack = navigator.items.collectAsStateWithLifecycle(
        context = Dispatchers.Main.immediate
    )

    // What a screen keeps in `rememberSaveable` — its scroll, its pager page —
    // outlives the screen leaving composition while it is still in the stack, so
    // Back finds it where it was. A screen leaving the stack takes its state
    // with it: the next visit is a new one, and a reader or book info opened
    // once does not hold its state for the rest of the activity.
    val saveableState = rememberSaveableStateHolder()
    val keysInStack = remember { mutableSetOf<Any>() }
    LaunchedEffect(stack.value) {
        val keys = stack.value.mapTo(mutableSetOf()) { it.stateKey(contentKey) }
        keysInStack.filterNot { it in keys }.forEach(saveableState::removeState)
        keysInStack.retainAll(keys)
        keysInStack.addAll(keys)
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        AnimatedContent(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            targetState = currentScreen.value,
            transitionSpec = {
                transitionSpec(this, lastEvent.value)
            },
            contentKey = contentKey,
            content = { screen ->
                saveableState.SaveableStateProvider(screen.stateKey(contentKey)) {
                    content(screen)
                }
            }
        )

        overlay()
    }

    BackHandler(enabled = backHandlerEnabled.invoke(currentScreen.value)) {
        navigator.pop()
    }
}

/** Screens sharing a [contentKey] share one saved state, as they share one slot. */
private fun Screen.stateKey(contentKey: (Screen) -> Any?): Any = contentKey(this) ?: this
