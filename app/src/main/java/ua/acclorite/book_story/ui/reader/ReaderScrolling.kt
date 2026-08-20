/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

/**
 * Whether the finger may still scroll the text.
 *
 * Giving up free scrolling is what turns this into a page-only reader, and a
 * page-only reader is only a reader while something can turn a page. So the
 * setting is honoured for exactly as long as a trigger remains: switch the last
 * one off and scrolling comes back rather than leaving the text answering to
 * nothing at all. The alternative — the setting outliving every way to advance —
 * is a state the reader cannot get out of from the reading screen.
 */
internal fun readerScrollEnabled(disableScrolling: Boolean, canTurnPage: Boolean): Boolean =
    !(disableScrolling && canTurnPage)
