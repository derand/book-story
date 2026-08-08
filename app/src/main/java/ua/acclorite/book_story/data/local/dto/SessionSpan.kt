/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

/** A session reduced to what the library-wide figures need of it. */
data class SessionSpan(
    val startTime: Long,
    val endTime: Long,
    val wordsRead: Int
)
