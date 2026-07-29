/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2026 derand
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.cache

import androidx.compose.ui.text.AnnotatedString
import ua.acclorite.book_story.domain.model.reader.ParsedText
import ua.acclorite.book_story.domain.model.reader.ReaderImage
import ua.acclorite.book_story.domain.model.reader.ReaderText
import ua.acclorite.book_story.domain.model.reader.ReaderTextRole
import ua.acclorite.book_story.domain.model.reader.TableAlignment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.util.UUID

/**
 * Binary (de)serializer for a whole [ParsedText] — the parsed element map plus
 * the footnote map — on top of [AnnotatedStringCodec].
 *
 * Images are stored as **metadata only** ([ReaderImage.id]/[ReaderImage.src] and
 * dimensions); the encoded bytes are not cached (they already live in the book
 * file and can be far larger than the text). A decoded image therefore carries
 * empty [ReaderImage.bytes] until they are loaded lazily from the source.
 *
 * [VERSION] guards the format; the parse cache additionally keys on a parser
 * version so a parser change invalidates stale entries.
 */
object ParsedTextCodec {

    // 3: tables carry per-column alignment.
    const val VERSION = 3

    private const val TYPE_CHAPTER = 0
    private const val TYPE_TEXT = 1
    private const val TYPE_POEM = 2
    private const val TYPE_TABLE = 3
    private const val TYPE_SEPARATOR = 4
    private const val TYPE_IMAGE = 5

    fun encode(parsed: ParsedText, out: DataOutput) {
        out.writeInt(VERSION)

        out.writeInt(parsed.text.size)
        parsed.text.forEach { element -> encodeElement(element, out) }

        out.writeInt(parsed.notes.size)
        parsed.notes.forEach { (key, value) ->
            out.writeUTF(key)
            AnnotatedStringCodec.encode(value, out)
        }
    }

    fun decode(input: DataInput): ParsedText {
        val version = input.readInt()
        require(version == VERSION) { "Unsupported ParsedText cache version: $version" }

        val textCount = input.readInt()
        val text = ArrayList<ReaderText>(textCount)
        repeat(textCount) { text.add(decodeElement(input)) }

        val noteCount = input.readInt()
        val notes = LinkedHashMap<String, AnnotatedString>(noteCount)
        repeat(noteCount) {
            val key = input.readUTF()
            notes[key] = AnnotatedStringCodec.decode(input)
        }

        return ParsedText(text = text, notes = notes)
    }

    fun encodeToBytes(parsed: ParsedText): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { encode(parsed, it) }
        return bytes.toByteArray()
    }

    fun decodeFromBytes(bytes: ByteArray): ParsedText =
        DataInputStream(ByteArrayInputStream(bytes)).use { decode(it) }

    private fun encodeElement(element: ReaderText, out: DataOutput) {
        when (element) {
            is ReaderText.Chapter -> {
                out.writeByte(TYPE_CHAPTER)
                out.writeLong(element.id.mostSignificantBits)
                out.writeLong(element.id.leastSignificantBits)
                out.writeUTF(element.title)
                out.writeInt(element.depth)
                val styledTitle = element.styledTitle
                out.writeBoolean(styledTitle != null)
                if (styledTitle != null) AnnotatedStringCodec.encode(styledTitle, out)
            }

            is ReaderText.Text -> {
                out.writeByte(TYPE_TEXT)
                encodeText(element, out)
            }

            is ReaderText.Poem -> {
                out.writeByte(TYPE_POEM)
                out.writeInt(element.lines.size)
                element.lines.forEach { line -> encodeText(line, out) }
            }

            is ReaderText.Table -> {
                out.writeByte(TYPE_TABLE)
                out.writeBoolean(element.hasHeader)
                out.writeInt(element.rows.size)
                element.rows.forEach { row ->
                    out.writeInt(row.size)
                    row.forEach { cell -> AnnotatedStringCodec.encode(cell, out) }
                }
                out.writeInt(element.alignments.size)
                element.alignments.forEach { alignment -> out.writeByte(alignment.ordinal) }
            }

            is ReaderText.Separator -> out.writeByte(TYPE_SEPARATOR)

            is ReaderText.Image -> {
                out.writeByte(TYPE_IMAGE)
                out.writeUTF(element.image.id)
                out.writeUTF(element.image.src)
                out.writeInt(element.image.width)
                out.writeInt(element.image.height)
                val caption = element.caption
                out.writeBoolean(caption != null)
                if (caption != null) encodeText(caption, out)
            }
        }
    }

    private fun decodeElement(input: DataInput): ReaderText {
        return when (val type = input.readByte().toInt()) {
            TYPE_CHAPTER -> {
                val id = UUID(input.readLong(), input.readLong())
                val title = input.readUTF()
                val depth = input.readInt()
                val styledTitle =
                    if (input.readBoolean()) AnnotatedStringCodec.decode(input) else null
                ReaderText.Chapter(
                    id = id,
                    title = title,
                    depth = depth,
                    styledTitle = styledTitle
                )
            }

            TYPE_TEXT -> decodeText(input)

            TYPE_POEM -> {
                val count = input.readInt()
                val lines = ArrayList<ReaderText.Text>(count)
                repeat(count) { lines.add(decodeText(input)) }
                ReaderText.Poem(lines)
            }

            TYPE_TABLE -> {
                val hasHeader = input.readBoolean()
                val rowCount = input.readInt()
                val rows = ArrayList<List<AnnotatedString>>(rowCount)
                repeat(rowCount) {
                    val cellCount = input.readInt()
                    val cells = ArrayList<AnnotatedString>(cellCount)
                    repeat(cellCount) { cells.add(AnnotatedStringCodec.decode(input)) }
                    rows.add(cells)
                }
                val alignmentCount = input.readInt()
                val alignments = ArrayList<TableAlignment>(alignmentCount)
                repeat(alignmentCount) {
                    alignments.add(TableAlignment.entries[input.readByte().toInt()])
                }
                ReaderText.Table(rows = rows, hasHeader = hasHeader, alignments = alignments)
            }

            TYPE_SEPARATOR -> ReaderText.Separator

            TYPE_IMAGE -> {
                val id = input.readUTF()
                val src = input.readUTF()
                val width = input.readInt()
                val height = input.readInt()
                val caption = if (input.readBoolean()) decodeText(input) else null
                ReaderText.Image(
                    image = ReaderImage(
                        id = id,
                        src = src,
                        // Loaded lazily from the source file on a cache hit.
                        bytes = ByteArray(0),
                        width = width,
                        height = height
                    ),
                    caption = caption
                )
            }

            else -> throw IllegalArgumentException("Unknown ReaderText type: $type")
        }
    }

    private fun encodeText(text: ReaderText.Text, out: DataOutput) {
        out.writeByte(text.role.ordinal)
        AnnotatedStringCodec.encode(text.line, out)
    }

    private fun decodeText(input: DataInput): ReaderText.Text {
        val role = ReaderTextRole.entries[input.readByte().toInt()]
        return ReaderText.Text(line = AnnotatedStringCodec.decode(input), role = role)
    }
}
