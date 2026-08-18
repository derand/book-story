/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.library

/**
 * What a library or history search query means, in one place, so that the two
 * lists cannot answer the same query differently.
 *
 * Deliberately not SQL. `LOWER()` in SQLite folds ASCII and nothing else, so a
 * `LIKE` over a lowered title is case-*sensitive* for every non-Latin script;
 * and `%` or `_` typed into the field would be read as wildcards. The library is
 * the same handful of rows every other screen already reads whole, so matching
 * it in Kotlin costs nothing and both problems stop existing.
 */
fun bookSearchTokens(query: String): List<String> =
    query.lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

/**
 * True when every token appears somewhere in the book's title or author.
 *
 * Per token rather than as one substring, so that either order of a name
 * matches, and so that a query can name a word of the title and a word of the
 * author at once. An empty [tokens] matches everything — that is the plain
 * unfiltered list.
 *
 * The author is searched because the library is already organised by it (see
 * [ua.acclorite.book_story.presentation.library.model.LibrarySortOrder]), and an
 * unknown one contributes nothing: its placeholder is a label, not the name of
 * anybody's book.
 *
 * The description is deliberately **not** searched. It is a different kind of
 * match — publisher's copy rather than the name of the thing being looked for —
 * in a list that has no ranking to keep the two apart; it exists for some
 * formats only (never for TXT or HTML); and it summarises plots, which is the
 * one thing a reader should not be shown by accident.
 */
fun Book.matchesSearch(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true

    val haystack = buildString {
        append(title)
        author.getAsString()?.let {
            append(' ')
            append(it)
        }
    }.lowercase()

    return tokens.all { haystack.contains(it) }
}
