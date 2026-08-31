/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import ua.acclorite.book_story.presentation.reader.model.ReaderVolumePaging
import ua.acclorite.book_story.ui.main.VolumeKeyPaging

/**
 * The reader's third page-turn trigger, beside the swipe and the tap zones, and
 * the only one that is not a gesture — see [VolumeKeyPaging] for why it is taken
 * on the activity rather than by a modifier here.
 *
 * It goes through the same [ReaderPager] as the other two, so a page turned by a
 * key is the same page turn in every respect.
 *
 * [enabled] is what hands the keys back to the system, and it must, because
 * while they are held the reader cannot change the volume: an open menu, a text
 * selection and a book still loading all give them up.
 */
@Composable
internal fun ReaderVolumeKeys(
    enabled: Boolean,
    volumePaging: ReaderVolumePaging,
    pager: ReaderPager
) {
    if (!enabled || volumePaging == ReaderVolumePaging.OFF) return

    DisposableEffect(volumePaging, pager) {
        val onVolumeKey: (Boolean) -> Unit = { volumeUp ->
            pager.turn(
                forward = when (volumePaging) {
                    ReaderVolumePaging.INVERSE -> volumeUp
                    else -> !volumeUp
                }
            )
        }

        VolumeKeyPaging.listen(onVolumeKey)
        onDispose { VolumeKeyPaging.stopListening(onVolumeKey) }
    }
}
