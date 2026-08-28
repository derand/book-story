/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.file.File
import ua.acclorite.book_story.domain.model.file.SourceLocality
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle

/**
 * Whether the app keeps this book's file itself.
 *
 * Shown for a book that is already a copy — it has to be, or there would be no
 * way back — and otherwise only where a copy buys something: a book read from
 * a cloud provider, or one whose source names no location at all. A book
 * sitting in a folder on this device raises no question worth asking.
 *
 * A copy whose origin was never recorded keeps the row, and says why it cannot
 * be turned off: there is nothing left to go back to.
 */
@Composable
fun BookInfoLayoutLocalCopy(
    book: Book,
    file: File?,
    changingLocalCopy: Boolean,
    toggleLocalCopy: (BookInfoEvent.OnToggleLocalCopy) -> Unit,
    refreshLocalCopy: (BookInfoEvent.OnRefreshLocalCopy) -> Unit
) {
    val context = LocalContext.current

    val canGoBack = book.originPath != null
    if (!book.isOwnCopy && !SourceLocality.offersLocalCopy(book.documentAuthority)) return

    val size = file?.size?.takeIf { it > 0 }?.let {
        Formatter.formatShortFileSize(context, it)
    }

    SwitchWithTitle(
        selected = book.isOwnCopy,
        enabled = !changingLocalCopy && (canGoBack || !book.isOwnCopy),
        title = stringResource(id = R.string.keep_local_copy_option),
        description = when {
            !book.isOwnCopy -> stringResource(id = R.string.keep_local_copy_book_desc)
            canGoBack -> stringResource(
                id = R.string.keep_local_copy_book_kept_desc,
                size.orEmpty()
            )

            else -> stringResource(
                id = R.string.keep_local_copy_book_kept_desc_no_origin,
                size.orEmpty()
            )
        }
    ) {
        toggleLocalCopy(BookInfoEvent.OnToggleLocalCopy)
    }

    // Only where there is something to refresh *from*. A copy does not notice
    // its original changing and cannot, so this is the whole answer to an
    // edited book, and it is asked for rather than guessed at.
    if (book.isOwnCopy && canGoBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !changingLocalCopy) {
                    refreshLocalCopy(BookInfoEvent.OnRefreshLocalCopy)
                }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            StyledText(
                text = stringResource(id = R.string.refresh_local_copy),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            StyledText(
                text = stringResource(id = R.string.refresh_local_copy_desc),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
