/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.statistics.BookStatistics
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.common.StyledText
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * What reading this book has actually amounted to. Every line is left out when
 * its figure cannot be trusted yet, so the card grows as the evidence does
 * rather than opening with a row of zeroes.
 */
@Composable
fun BookInfoLayoutStatistics(
    statistics: BookStatistics,
    setFinished: (BookInfoEvent.OnSetFinished) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StyledText(
            text = stringResource(id = R.string.statistics),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        if (statistics.isEmpty) {
            StyledText(
                text = stringResource(id = R.string.statistics_none),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        } else {
            StatisticsLine(
                label = stringResource(id = R.string.statistics_time),
                value = formatDuration(statistics.totalTimeMs)
            )

            StatisticsLine(
                label = stringResource(id = R.string.statistics_read),
                value = stringResource(
                    R.string.statistics_read_value,
                    (statistics.coveragePercent * 100).roundToInt(),
                    statistics.coveredWords
                )
            )

            statistics.wordsPerMinute?.let { pace ->
                StatisticsLine(
                    label = stringResource(id = R.string.statistics_pace),
                    value = statistics.typicalWordsPerMinute.let { typical ->
                        // Only worth comparing once the two can differ meaningfully:
                        // a dense book reads slower than fiction and that is the
                        // interesting part, not a 1 wpm gap.
                        if (typical == null || typical == pace) {
                            stringResource(R.string.statistics_pace_value, pace)
                        } else {
                            stringResource(R.string.statistics_pace_value_compared, pace, typical)
                        }
                    }
                )
            }

            statistics.timeLeftMs?.takeIf { !statistics.finished }?.let { left ->
                StatisticsLine(
                    label = stringResource(id = R.string.statistics_left),
                    value = statistics.finishedBy.let { by ->
                        if (by == null) formatDuration(left)
                        else stringResource(
                            R.string.statistics_left_value_by,
                            formatDuration(left),
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(by))
                        )
                    }
                )
            }

            StatisticsLine(
                label = stringResource(id = R.string.statistics_sessions),
                value = statistics.sessions.toString()
            )
        }

        // Outside the branch above on purpose: "finished" is the reader's own
        // statement, not one of the measurements, so it has to be available on a
        // book this build has never measured — which, there being no backfill,
        // is every book already in the library.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledText(
                text = stringResource(id = R.string.statistics_finished),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Switch(
                checked = statistics.finished,
                onCheckedChange = { setFinished(BookInfoEvent.OnSetFinished(it)) }
            )
        }
    }
}

@Composable
private fun StatisticsLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StyledText(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        StyledText(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.duration_under_a_minute)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        minutes % 60 == 0L -> stringResource(R.string.duration_hours, minutes / 60)
        else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
