/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream

/**
 * Binary (de)serializer for the [AnnotatedString]s produced by the book parser.
 *
 * The parser emits only a small, fixed set of inline styles, so every
 * [SpanStyle] it can produce packs losslessly into a single [Int] bitmask
 * ([toStyleMask]). This keeps the cache compact — text as UTF-8 plus 12 bytes
 * per span — and avoids a general (and heavy) style serializer.
 *
 * Writes/reads through [DataOutput]/[DataInput] so it composes into the larger
 * `ParsedText` cache format. IMPORTANT: this only covers the styles the parser
 * actually emits; extend the mask (and bump the cache's parser version) if the
 * parser starts producing new ones.
 */
object AnnotatedStringCodec {

    private const val BOLD = 1 shl 0        // FontWeight.Medium
    private const val ITALIC = 1 shl 1      // FontStyle.Italic
    private const val MONOSPACE = 1 shl 2   // FontFamily.Monospace
    private const val UNDERLINE = 1 shl 3   // TextDecoration.Underline
    private const val LINE_THROUGH = 1 shl 4 // TextDecoration.LineThrough
    private const val SUBSCRIPT = 1 shl 5   // BaselineShift.Subscript
    private const val SUPERSCRIPT = 1 shl 6 // BaselineShift.Superscript
    private const val SMALL_FONT = 1 shl 7  // fontSize 0.75.em (sub/sup, note refs)

    private const val LINK_URL: Int = 0
    private const val LINK_CLICKABLE: Int = 1

    /** Font size the parser uses for sub/superscript and note markers. */
    private val SMALL_EM = 0.75.em

    fun encode(value: AnnotatedString, out: DataOutput) {
        val bytes = value.text.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)

        val spans = value.spanStyles
        out.writeInt(spans.size)
        spans.forEach { range ->
            out.writeInt(range.start)
            out.writeInt(range.end)
            out.writeInt(range.item.toStyleMask())
        }

        val links = value.getLinkAnnotations(0, value.length)
        out.writeInt(links.size)
        links.forEach { range ->
            out.writeInt(range.start)
            out.writeInt(range.end)
            when (val link = range.item) {
                is LinkAnnotation.Url -> {
                    out.writeByte(LINK_URL)
                    out.writeUTF(link.url)
                    out.writeInt(link.styles?.style.toStyleMask())
                }

                is LinkAnnotation.Clickable -> {
                    out.writeByte(LINK_CLICKABLE)
                    out.writeUTF(link.tag)
                    out.writeInt(link.styles?.style.toStyleMask())
                }

                else -> throw IllegalArgumentException(
                    "Unsupported link annotation: ${link::class.java}"
                )
            }
        }
    }

    fun decode(input: DataInput): AnnotatedString {
        val textBytes = ByteArray(input.readInt())
        input.readFully(textBytes)
        val text = String(textBytes, Charsets.UTF_8)

        val spanCount = input.readInt()
        return buildAnnotatedString {
            append(text)

            repeat(spanCount) {
                val start = input.readInt()
                val end = input.readInt()
                val mask = input.readInt()
                addStyle(mask.toSpanStyle(), start, end)
            }

            val linkCount = input.readInt()
            repeat(linkCount) {
                val start = input.readInt()
                val end = input.readInt()
                val kind = input.readByte().toInt()
                val target = input.readUTF()
                val styles = TextLinkStyles(input.readInt().toSpanStyle())
                when (kind) {
                    LINK_URL -> addLink(LinkAnnotation.Url(target, styles), start, end)
                    LINK_CLICKABLE -> addLink(
                        LinkAnnotation.Clickable(target, styles, linkInteractionListener = null),
                        start, end
                    )

                    else -> throw IllegalArgumentException("Unknown link kind: $kind")
                }
            }
        }
    }

    /** Convenience wrapper: whole [AnnotatedString] to a standalone byte array. */
    fun encodeToBytes(value: AnnotatedString): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { encode(value, it) }
        return bytes.toByteArray()
    }

    /** Reverses [encodeToBytes]. */
    fun decodeFromBytes(bytes: ByteArray): AnnotatedString =
        DataInputStream(ByteArrayInputStream(bytes)).use { decode(it) }

    private fun SpanStyle?.toStyleMask(): Int {
        if (this == null) return 0
        var mask = 0
        if (fontWeight == FontWeight.Medium) mask = mask or BOLD
        if (fontStyle == FontStyle.Italic) mask = mask or ITALIC
        if (fontFamily == FontFamily.Monospace) mask = mask or MONOSPACE
        textDecoration?.let { decoration ->
            if (decoration.contains(TextDecoration.Underline)) mask = mask or UNDERLINE
            if (decoration.contains(TextDecoration.LineThrough)) mask = mask or LINE_THROUGH
        }
        if (baselineShift == BaselineShift.Subscript) mask = mask or SUBSCRIPT
        if (baselineShift == BaselineShift.Superscript) mask = mask or SUPERSCRIPT
        if (fontSize == SMALL_EM) mask = mask or SMALL_FONT
        return mask
    }

    private fun Int.toSpanStyle(): SpanStyle {
        val decorations = buildList {
            if (this@toSpanStyle and UNDERLINE != 0) add(TextDecoration.Underline)
            if (this@toSpanStyle and LINE_THROUGH != 0) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            fontWeight = if (this and BOLD != 0) FontWeight.Medium else null,
            fontStyle = if (this and ITALIC != 0) FontStyle.Italic else null,
            fontFamily = if (this and MONOSPACE != 0) FontFamily.Monospace else null,
            textDecoration = when (decorations.size) {
                0 -> null
                1 -> decorations[0]
                else -> TextDecoration.combine(decorations)
            },
            baselineShift = when {
                this and SUBSCRIPT != 0 -> BaselineShift.Subscript
                this and SUPERSCRIPT != 0 -> BaselineShift.Superscript
                else -> null
            },
            fontSize = if (this and SMALL_FONT != 0) SMALL_EM else TextUnit.Unspecified
        )
    }
}
