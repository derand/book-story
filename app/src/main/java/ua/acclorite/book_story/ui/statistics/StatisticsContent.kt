/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.statistics.LibraryStatistics
import ua.acclorite.book_story.ui.common.components.common.StyledText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsContent(
    statistics: LibraryStatistics?,
    navigateBack: () -> Unit
) {
    BackHandler { navigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { StyledText(text = stringResource(id = R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.go_back_content_desc)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (statistics == null) return@Column

            if (statistics.isEmpty) {
                StyledText(
                    text = stringResource(id = R.string.statistics_none_library),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                return@Column
            }

            StatisticsRow(
                label = stringResource(id = R.string.statistics_total_time),
                value = formatLongDuration(statistics.totalTimeMs)
            )
            StatisticsRow(
                label = stringResource(id = R.string.statistics_words_read),
                value = statistics.wordsRead.toString()
            )
            statistics.wordsPerMinute?.let {
                StatisticsRow(
                    label = stringResource(id = R.string.statistics_your_pace),
                    value = stringResource(R.string.statistics_pace_value, it)
                )
            }
            StatisticsRow(
                label = stringResource(id = R.string.statistics_books_finished),
                value = statistics.booksRead.toString()
            )
            StatisticsRow(
                label = stringResource(id = R.string.statistics_days_read),
                value = statistics.daysRead.toString()
            )

            Spacer(Modifier.height(8.dp))
            StyledText(
                text = stringResource(id = R.string.statistics_includes_deleted),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun StatisticsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StyledText(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        StyledText(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

/** Hours and minutes; a lifetime of reading outgrows minutes quickly. */
@Composable
fun formatLongDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.duration_under_a_minute)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        minutes % 60 == 0L -> stringResource(R.string.duration_hours, minutes / 60)
        else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
