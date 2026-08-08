/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

/** Just enough of a session to work out a pace from it. */
data class SessionPace(
    val durationMs: Long,
    val wordsRead: Int
)
