/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader

import android.content.pm.ActivityInfo
import android.os.Parcelable
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.core.helpers.calculateProgress
import ua.acclorite.book_story.core.log.BookOpenTrace
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.reader.model.ReaderProgressCount
import ua.acclorite.book_story.presentation.settings.ColorPresetsModel
import ua.acclorite.book_story.domain.model.reader.activeColorPreset
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.helpers.ProvideBookImages
import ua.acclorite.book_story.ui.common.helpers.setBrightness
import ua.acclorite.book_story.ui.reader.ReaderContent
import ua.acclorite.book_story.ui.reader.ReaderEffects
import kotlin.math.roundToInt

@Parcelize
data class ReaderScreen(val bookId: Int) : Screen, Parcelable {

    companion object {
        const val CHAPTERS_DRAWER = "chapters_drawer"
        const val SETTINGS_BOTTOM_SHEET = "settings_bottom_sheet"
        const val NOTE_BOTTOM_SHEET = "note_bottom_sheet"
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<ReaderModel>()
        val colorPresetsModel = hiltViewModel<ColorPresetsModel>()
        val settings = LocalSettings.current

        val state = screenModel.state.collectAsStateWithLifecycle()
        val colorPresetsState = colorPresetsModel.state.collectAsStateWithLifecycle()

        val activity = checkNotNull(LocalActivity.current)
        val lifecycleOwner = LocalLifecycleOwner.current
        // The model scrolls this very object, so the screen must not swap it for
        // a restored copy.
        val listState = state.value.listState
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    consumed.y.let { velocity ->
                        if (velocity in -70f..70f) return@let
                        if (!state.value.showMenu) return@let
                        if (state.value.lockMenu) return@let
                        if (!settings.hideBarsOnFastScroll.lastValue) return@let

                        screenModel.onEvent(
                            ReaderEvent.OnMenuVisibility(
                                show = false,
                                saveCheckpoint = false
                            )
                        )
                    }
                    return super.onPostScroll(consumed, available, source)
                }
            }
        }

        // A built-in preset auto-follows the theme; a custom one is used as-is.
        val activePreset = colorPresetsState.value.colorPresets.activeColorPreset(
            selected = colorPresetsState.value.selectedColorPreset,
            isDark = settings.darkTheme.value.isDark()
        )
        val backgroundColor = animateColorAsState(
            targetValue = activePreset.backgroundColor
        )
        val fontColor = animateColorAsState(
            targetValue = activePreset.fontColor
        )
        val look = settings.readerLook(
            fontColor = fontColor.value,
            backgroundColor = backgroundColor.value
        )
        val controls = settings.readerControls()

        // Read in composition on purpose: the insets are state, so a rotation
        // or the system bars coming back recomposes with the new values.
        val noPadding = PaddingValues(0.dp)
        val systemBarsPadding =
            if (settings.fullscreen.value) noPadding
            else WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
        val cutoutPadding =
            if (settings.cutoutPadding.value) WindowInsets.displayCutout.asPaddingValues()
            else noPadding
        val contentPadding = systemBarsPadding.orWhereZero(
            fallback = cutoutPadding,
            layoutDirection = LocalLayoutDirection.current
        )

        val bookProgress = remember(
            state.value.book.progress,
            state.value.text,
            settings.progressCount.value
        ) {
            when (settings.progressCount.lastValue) {
                ReaderProgressCount.PERCENTAGE -> {
                    "${state.value.book.progress.calculateProgress(2)}%"
                }

                ReaderProgressCount.QUANTITY -> {
                    val index =
                        (state.value.book.progress * state.value.text.lastIndex + 1).roundToInt()
                    "$index / ${state.value.text.size}"
                }
            }
        }
        val chapterProgress = remember(
            state.value.text,
            state.value.book.progress,
            state.value.currentChapter,
            state.value.currentChapterProgress,
            settings.progressCount.value
        ) {
            if (state.value.currentChapter == null) return@remember ""
            when (settings.progressCount.lastValue) {
                ReaderProgressCount.PERCENTAGE -> {
                    " (${state.value.currentChapterProgress.calculateProgress(2)}%)"
                }

                ReaderProgressCount.QUANTITY -> {
                    val (index, length) = screenModel.findChapterIndexAndLength(
                        (state.value.book.progress * state.value.text.lastIndex).roundToInt()
                    ).apply { if (first == -1 && second == -1) return@remember "" }
                    " (${index} / ${length})"
                }
            }
        }
        val progress = remember(bookProgress, chapterProgress) {
            "${bookProgress}${chapterProgress}"
        }

        LaunchedEffect(Unit) {
            BookOpenTrace.mark("reader screen composed")
            screenModel.init(bookId = bookId)
        }
        LaunchedEffect(settings.fullscreen.value) {
            screenModel.onEvent(
                ReaderEvent.OnMenuVisibility(
                    show = state.value.showMenu,
                    saveCheckpoint = false
                )
            )
        }
        LaunchedEffect(listState) {
            screenModel.updateProgress(listState)
        }
        // Images of a book restored from the parse cache are loaded in the
        // background, but only when they are shown at all — also re-run when the
        // reader turns them back on mid-book.
        LaunchedEffect(settings.images.value, state.value.text) {
            if (settings.images.lastValue) screenModel.onEvent(ReaderEvent.OnLoadImages)
        }

        // The only place that notices the reading stopping without the reader
        // being left: a call, the home gesture, the screen going off.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> screenModel.onEnterForeground()
                    Lifecycle.Event.ON_STOP -> screenModel.onLeaveForeground()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        DisposableEffect(settings.screenOrientation.value) {
            activity.requestedOrientation = settings.screenOrientation.lastValue.code
            onDispose {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        DisposableEffect(
            settings.screenBrightness.value,
            settings.customScreenBrightness.value
        ) {
            when (settings.customScreenBrightness.lastValue) {
                true -> activity.setBrightness(brightness = settings.screenBrightness.lastValue)
                false -> activity.setBrightness(brightness = null)
            }

            onDispose {
                activity.setBrightness(brightness = null)
            }
        }
        DisposableEffect(settings.keepScreenOn.value) {
            when (settings.keepScreenOn.lastValue) {
                true -> activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                false -> activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                screenModel.clearAsync(bookId = bookId)
                WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                ).show(WindowInsetsCompat.Type.systemBars())
            }
        }

        ReaderEffects(
            effects = screenModel.effects,
            screen = this,
            book = state.value.book,
            fullscreen = settings.fullscreen.value,
            grantFolder = screenModel::onEvent
        )

        ProvideBookImages(screenModel.imageStore) {
            ReaderContent(
                book = state.value.book,
                text = state.value.text,
                bottomSheet = state.value.bottomSheet,
                drawer = state.value.drawer,
                currentNote = state.value.currentNote,
                fullscreenImage = state.value.fullscreenImage,
                listState = listState,
                currentChapter = state.value.currentChapter,
                nestedScrollConnection = nestedScrollConnection,
                currentChapterProgress = state.value.currentChapterProgress,
                isLoading = state.value.isLoading,
                errorMessage = state.value.errorMessage,
                errorOffersPathChange = state.value.errorOffersPathChange,
                checkpoints = state.value.checkpoints,
                search = state.value.search,
                showMenu = state.value.showMenu,
                lockMenu = state.value.lockMenu,
                contentPadding = contentPadding,
                look = look,
                controls = controls,
                progress = progress,
                switchColorPreset = colorPresetsModel::onEvent,
                onEvent = screenModel::onEvent,
                addingToLibrary = state.value.addingToLibrary || state.value.lockMenu
            )
        }
    }
}

/**
 * Per side: this padding where it is not zero, [fallback]'s otherwise — the
 * system bars already cover the cutout on their own side, so the cutout counts
 * only where there is no bar.
 */
internal fun PaddingValues.orWhereZero(
    fallback: PaddingValues,
    layoutDirection: LayoutDirection
): PaddingValues {
    fun Dp.or(other: Dp) = if (this == 0.dp) other else this
    return PaddingValues(
        start = calculateStartPadding(layoutDirection)
            .or(fallback.calculateStartPadding(layoutDirection)),
        top = calculateTopPadding().or(fallback.calculateTopPadding()),
        end = calculateEndPadding(layoutDirection)
            .or(fallback.calculateEndPadding(layoutDirection)),
        bottom = calculateBottomPadding().or(fallback.calculateBottomPadding())
    )
}
