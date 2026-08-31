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
 * Whether the volume keys turn pages, and which key goes forward. `ON` reads the
 * keys the way the text moves — volume down goes further down the book — and
 * `INVERSE` swaps them, because readers genuinely disagree about which key is
 * forward and neither convention is more right than the other.
 *
 * Off by default, and for a reason that is not caution: while this is on the
 * reader has no way to change the volume, which the reader who never asked for
 * page keys would experience purely as a loss.
 */
enum class ReaderVolumePaging(@StringRes val title: Int) {
    OFF(R.string.volume_paging_off),
    ON(R.string.volume_paging_on),
    INVERSE(R.string.volume_paging_inverse)
}
