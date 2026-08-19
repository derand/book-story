/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.reader.model

import androidx.annotation.StringRes
import ua.acclorite.book_story.R

/**
 * Whether a tap near the left or right edge turns a page, and which edge goes
 * which way. Off by default: the reader is a scrolling one, and a tap that used
 * to open the menu must keep doing so until it is asked not to.
 */
enum class ReaderTapPaging(@StringRes val title: Int) {
    OFF(R.string.tap_paging_off),
    ON(R.string.tap_paging_on),
    INVERSE(R.string.tap_paging_inverse)
}
