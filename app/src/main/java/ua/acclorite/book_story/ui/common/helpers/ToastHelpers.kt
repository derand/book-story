/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.common.helpers

import android.content.Context
import android.widget.Toast

/**
 * The toast on screen, cancelled by the next one. Made on the application
 * context, so it does not keep a finished Activity alive (#167).
 */
private var toast: Toast? = null

fun String.showToast(context: Context, longToast: Boolean = true) {
    toast?.cancel()
    toast = null

    toast = Toast.makeText(context.applicationContext, this, if (longToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
    toast?.show()
}