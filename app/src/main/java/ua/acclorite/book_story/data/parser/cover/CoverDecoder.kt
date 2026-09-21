/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.cover

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ua.acclorite.book_story.core.CoverImage

/**
 * The longest side worth keeping of a cover, in pixels.
 *
 * Taken from where a cover is drawn **sharp**, which is Book info: 220 dp tall,
 * so 880 px on the densest screens that ship (xxxhdpi, 4×) and 330 px on the
 * tablet this was measured on. The library grid and the history list are
 * smaller again.
 *
 * The one place a cover is drawn larger is the blurred backdrop behind Book
 * info — the screen's full width, but behind a 3 dp blur at 40 % opacity under
 * a gradient, which is not a picture that wants resolution. Picking the sharp
 * draw and letting the backdrop scale up is the trade this number makes, and
 * [coverSampleSize] only halves, so what is kept is usually larger still.
 */
const val COVER_TARGET_PX = 1024

/**
 * The power-of-two subsampling that keeps a [width]×[height] image at or above
 * [target] on its longest side.
 *
 * `inSampleSize` is what makes the decode itself cheap — the full-size bitmap is
 * never allocated — at the price of only being able to halve: a 6000 px cover
 * comes back at 1500 px, not at 1024. Erring large is the right way round, since
 * the result is compressed to WEBP straight after and never drawn at more than a
 * fraction of it.
 *
 * Returns 1 for anything already at or below [target], so a small cover is never
 * touched and never upscaled.
 */
internal fun coverSampleSize(width: Int, height: Int, target: Int = COVER_TARGET_PX): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1

    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= target) sample *= 2
    return sample
}

/**
 * Decodes [bytes] as a cover, or null when they are not an image.
 *
 * Bounds first and then a sampled decode, which is what
 * `DocumentParser.loadImage` already does for the images inside a book. Without
 * it a cover is decoded at whatever the book holds: a 6000×6000 one costs 144 MB
 * of heap at `ARGB_8888` to be drawn, in the end, at 330 px.
 *
 * `RGB_565` is the config the cover is stored in anyway — everything here ends
 * as a WEBP with no alpha — so asking for it in the decode is what lets the
 * caller compress the bitmap it is handed instead of copying it first.
 */
fun decodeCover(bytes: ByteArray): CoverImage? {
    if (bytes.isEmpty()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, coverOptions(bounds))
}

/**
 * Decodes the image at [uri] as a cover, or null when it cannot be read.
 *
 * Two opens, because the bounds pass consumes the stream and a provider's stream
 * cannot be rewound. The second open is the cheap one: it is the only decode that
 * allocates anything.
 */
fun decodeCover(contentResolver: ContentResolver, uri: Uri): CoverImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // The bounds pass returns no bitmap — that is what asking for bounds means —
    // so what is checked is the stream, and the sizes it filled in.
    val boundsStream = contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    return contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, coverOptions(bounds))
    }
}

private fun coverOptions(bounds: BitmapFactory.Options) = BitmapFactory.Options().apply {
    inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight)
    inPreferredConfig = Bitmap.Config.RGB_565
}
