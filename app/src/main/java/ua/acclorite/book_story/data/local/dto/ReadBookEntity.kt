/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per book that has ever been read — the durable layer, never deleted.
 * It is what lets a book leave a trace after its file is gone: the shelf of
 * everything read, and the "books read" count.
 *
 * Written from the first session and updated as reading goes, *not* folded in
 * at deletion time: [finished] has to be recorded when it happens, because by
 * the time a book is deleted the reason is lost — one abandoned at 40 % looks
 * exactly like one whose file simply moved.
 *
 * The per-book totals here duplicate a `SUM` over that book's sessions, which is
 * unavoidable: once those sessions are anonymised, that `SUM` no longer exists.
 *
 * The index on [bookId] is **unique**, which states the invariant rather than
 * trusting every writer to keep it: one record per live book. Deleted books are
 * exempt by construction — SQLite counts NULLs as distinct, so any number of
 * anonymised rows sit under it happily, which is exactly the rule wanted.
 */
@Entity(indices = [Index(value = ["bookId"], unique = true)])
data class ReadBookEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    /** The live book, or null once it has been deleted from the library. */
    val bookId: Int?,
    val title: String,
    val author: String,
    val totalTimeMs: Long,
    val totalWords: Int,
    val sessions: Int,
    val firstReadAt: Long,
    val lastReadAt: Long,
    /**
     * A statement by the reader, not a measurement: set automatically on
     * reaching the end, and toggleable by hand at any time. People skip
     * appendices, abandon at 97 %, or finish someone else's foreword.
     */
    val finished: Boolean,
    /** Snapshot of coverage; the interval detail dies with the book. */
    val coveragePercent: Float
)
