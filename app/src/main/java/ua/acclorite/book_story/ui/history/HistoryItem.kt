/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.history.History
import ua.acclorite.book_story.ui.common.components.common.BookCover
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.helpers.noRippleClickable
import ua.acclorite.book_story.ui.statistics.formatLongDuration
import java.util.Date

@Composable
fun LazyItemScope.HistoryItem(
    historyEntry: History,
    isRefreshing: Boolean,
    onBodyClick: () -> Unit,
    onTitleClick: () -> Unit
) {
    // The user's 12/24-hour setting, not a fixed "HH:mm".
    val context = LocalContext.current
    val time = remember(historyEntry.lastReadAt) {
        DateFormat.getTimeFormat(context).format(Date(historyEntry.lastReadAt))
    }
    val duration = formatLongDuration(historyEntry.timeMs)
    val words = pluralStringResource(
        R.plurals.history_words_plural,
        historyEntry.words,
        historyEntry.words
    )
    val sessions = pluralStringResource(
        R.plurals.history_sessions_plural,
        historyEntry.sessions,
        historyEntry.sessions
    )
    // A session spent on images alone earns no words; "0 words" says nothing,
    // and neither does "1 session". On a phone the line wraps: each part is
    // kept whole ("3501 words", not "3501 / words"), and the dot stays at the
    // end of a line rather than starting the next one.
    val summary = listOfNotNull(
        time,
        duration,
        words.takeIf { historyEntry.words > 0 },
        sessions.takeIf { historyEntry.sessions > 1 }
    ).joinToString("\u00A0· ") { it.replace(' ', '\u00A0') }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .clickable(enabled = !isRefreshing) {
                onBodyClick()
            }
            .padding(top = 6.dp, bottom = 6.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .height(90.dp)
                    .width(60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(10.dp)
                    )
            ) {
                BookCover(coverImage = historyEntry.book.coverImage, shape = RoundedCornerShape(10.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight()
            ) {
                StyledText(
                    text = historyEntry.book.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable(
                            enabled = !isRefreshing,
                            onClick = onTitleClick
                        ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 21.sp
                    ),
                    maxLines = 2
                )
                StyledText(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    // Two lines: on a phone the session count would be cut off.
                    maxLines = 2
                )
            }
        }
    }
}
