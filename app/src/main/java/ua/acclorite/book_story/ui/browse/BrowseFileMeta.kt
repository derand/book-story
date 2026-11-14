/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.browse

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ua.acclorite.book_story.presentation.browse.model.SelectableFile

/**
 * A file's size and date, as "1.2 MB, 13:31 6 Nov 2026" ([withTime]) or
 * "1.2 MB, 6 Nov 2026". Kept only while the file's size and date stay the
 * same: an item is keyed by path, and a file changed under it must not go on
 * showing its old size.
 */
@Composable
fun rememberFileMeta(file: SelectableFile, withTime: Boolean): String {
    val context = LocalContext.current
    return remember(file.size, file.lastModified, withTime) {
        fileMeta(context, file.size, file.lastModified, withTime)
    }
}

/** The platform's own formats: the locale's units and the 12/24-hour setting. */
internal fun fileMeta(context: Context, size: Long, lastModified: Long, withTime: Boolean): String {
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
            DateUtils.FORMAT_ABBREV_MONTH or (if (withTime) DateUtils.FORMAT_SHOW_TIME else 0)
    val date = DateUtils.formatDateTime(context, lastModified, flags)
    return "${Formatter.formatShortFileSize(context, size)}, $date"
}
