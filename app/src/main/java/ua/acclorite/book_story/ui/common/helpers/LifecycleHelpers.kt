/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.helpers

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.core.net.toUri
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.messageForLog

/**
 * Starts this intent from [activity], in its task: a share sheet or another
 * app's screen opens over the app and Back returns to it. (It used to start
 * through `baseContext` with `FLAG_ACTIVITY_NEW_TASK`, so every one opened as a
 * separate task — #167.)
 */
inline fun Intent.launchActivity(
    activity: Activity,
    createChooser: Boolean = false,
    success: () -> Unit = {},
    error: () -> Unit = {}
) {
    try {
        val intent = if (createChooser) Intent.createChooser(this, "") else this
        activity.startActivity(intent)
    } catch (e: Exception) {
        logE("LifecycleHelpers", e.messageForLog())
        error()
        return
    }

    success()
}

/** Opens [url] in a browser, or shows a toast when none can open it. */
fun Activity.openInBrowser(url: String) {
    Intent(Intent.ACTION_VIEW, url.toUri()).launchActivity(
        activity = this,
        error = {
            getString(R.string.error_no_browser).showToast(context = this, longToast = false)
        }
    )
}

fun Activity.setBrightness(brightness: Float?) {
    window.attributes.apply {
        screenBrightness = brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = this
    }
}