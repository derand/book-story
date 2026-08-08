/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.model.statistics

/**
 * Coverage is a set of item indices while a book is open — adding to it is a
 * single call and asking "was this read" is free — and a string of ranges on
 * disk, where a fully read book is a handful of bytes rather than tens of
 * thousands of numbers.
 *
 * The format is ascending, non-overlapping, comma-separated: `"12-480,3900"`.
 * A single index is written on its own, without a redundant `12-12`.
 */
object CoverageCodec {

    fun encode(covered: Set<Int>): String {
        if (covered.isEmpty()) return ""

        val sorted = covered.toIntArray()
        sorted.sort()

        return buildString {
            var start = sorted.first()
            var end = start

            for (index in 1 until sorted.size) {
                val value = sorted[index]
                if (value == end + 1) {
                    end = value
                    continue
                }

                appendRange(start, end)
                append(',')
                start = value
                end = value
            }

            appendRange(start, end)
        }
    }

    /**
     * Tolerant on purpose: this string has been through a database, and a
     * malformed piece of it should cost the range it describes, not the whole
     * reading history of the book.
     */
    fun decode(encoded: String): Set<Int> {
        if (encoded.isBlank()) return emptySet()

        val covered = mutableSetOf<Int>()
        for (part in encoded.split(',')) {
            val range = part.trim()
            if (range.isEmpty()) continue

            val dash = range.indexOf('-')
            if (dash == -1) {
                range.toIntOrNull()?.let { covered.add(it) }
                continue
            }

            val start = range.substring(0, dash).trim().toIntOrNull() ?: continue
            val end = range.substring(dash + 1).trim().toIntOrNull() ?: continue
            if (end < start) continue

            for (index in start..end) covered.add(index)
        }

        return covered
    }

    private fun StringBuilder.appendRange(start: Int, end: Int) {
        append(start)
        if (end != start) {
            append('-')
            append(end)
        }
    }
}
