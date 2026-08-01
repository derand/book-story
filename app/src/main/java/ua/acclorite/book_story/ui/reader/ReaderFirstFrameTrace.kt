/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import android.os.Build
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import ua.acclorite.book_story.core.log.BOOK_TIMING
import ua.acclorite.book_story.core.log.BookOpenTrace

/**
 * Closes the [BookOpenTrace] at the first frame that actually carries text —
 * the moment the reader stops being a blank screen, which is the number a
 * reader feels and the one nothing else measures.
 *
 * Composition is not that moment: it only decides what the frame will contain.
 * So the mark waits for the frame to be handed to the display, one vsync later
 * on API 29+, or for the start of the following frame below it.
 *
 * Compiles to nothing when [BOOK_TIMING] is off.
 */
@Composable
fun ReaderFirstFrameTrace(hasText: Boolean) {
    if (!BOOK_TIMING) return
    val view = LocalView.current

    DisposableEffect(hasText) {
        if (!hasText) return@DisposableEffect onDispose { }

        val mark = Runnable { BookOpenTrace.end("first frame with text") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Fires once, when the frame is committed, and removes itself.
            view.viewTreeObserver.registerFrameCommitCallback(mark)
            onDispose { view.viewTreeObserver.unregisterFrameCommitCallback(mark) }
        } else {
            val callback = Choreographer.FrameCallback { mark.run() }
            Choreographer.getInstance().postFrameCallback(callback)
            onDispose { Choreographer.getInstance().removeFrameCallback(callback) }
        }
    }
}
