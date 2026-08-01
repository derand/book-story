/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.document

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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import ua.acclorite.book_story.core.helpers.rethrowIfCancellation
import ua.acclorite.book_story.core.log.TimingSum
import ua.acclorite.book_story.domain.model.reader.ANCHOR_LINK_TAG_PREFIX
import ua.acclorite.book_story.domain.model.reader.NOTE_LINK_TAG_PREFIX
import javax.inject.Inject

/** Span styles toggled by the private-use marks embedded in the text. */
private val MARK_STYLES = mapOf(
    STRIKETHROUGH_MARK.single() to SpanStyle(
        textDecoration = TextDecoration.LineThrough
    ),
    SUBSCRIPT_MARK.single() to SpanStyle(
        baselineShift = BaselineShift.Subscript,
        fontSize = 0.75.em
    ),
    SUPERSCRIPT_MARK.single() to SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = 0.75.em
    ),
    ITALIC_MARK.single() to SpanStyle(
        fontStyle = FontStyle.Italic
    ),
    BOLD_MARK.single() to SpanStyle(
        // Same weight as a <strong>/StrongEmphasis run
        fontWeight = FontWeight.Medium
    )
)

private val NOTE_REF_CHAR = NOTE_REF_MARK.single()
private val ANCHOR_REF_CHAR = ANCHOR_REF_MARK.single()
private val REF_SEPARATOR_CHAR = REF_SEPARATOR.single()
private val REF_END_CHAR = REF_END_MARK.single()

class MarkdownParser @Inject constructor(
    private val commonmarkParser: Parser
) {
    /**
     * How the per-line cost of [parse] divides — commonmark building its own
     * tree, against walking that tree into an [AnnotatedString]. The caller owns
     * the sums, because only it knows what one document is: see
     * [DocumentParser.parseDocument], which runs [parse] once per line of the
     * book and is where the numbers behind issue #26 come from.
     */
    val commonmarkSum = TimingSum("commonmark")
    val annotateSum = TimingSum("annotate")

    fun resetTiming() {
        commonmarkSum.reset()
        annotateSum.reset()
    }

    /**
     * Parses markdown text to [AnnotatedString].
     *
     * @return Parsed annotated string.
     */
    fun parse(markdown: String): AnnotatedString {
        return try {
            val document = commonmarkSum.add { commonmarkParser.parse(markdown) }
            val annotatedString = annotateSum.add {
                buildAnnotatedString {
                    parseNode(document, MarkScanner(this))
                }.ifBlank { buildAnnotatedString { append(markdown) } }
                    .trim() as AnnotatedString
            }

            annotatedString
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            e.printStackTrace()
            buildAnnotatedString { append(markdown) }
        }
    }

    /**
     * Parses [Node].
     * Appends text and applies styles to the target [AnnotatedString.Builder].
     */
    private fun AnnotatedString.Builder.parseNode(node: Node, scanner: MarkScanner) {
        when (node) {
            is Heading, is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    parseChildren(node, scanner)
                }
            }

            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    parseChildren(node, scanner)
                }
            }

            is Code -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(node.literal)
                }
            }

            is Link -> {
                withLink(
                    LinkAnnotation.Url(
                        node.destination,
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                    )
                ) {
                    parseChildren(node, scanner)
                }
            }

            is Text -> {
                // Appended verbatim: commonmark has already consumed every
                // "*"/"_" that was real markup, so whatever is left is the
                // author's own punctuation — "(*)" must not become "()".
                scanner.append(node.literal)
                parseChildren(node, scanner)
            }

            else -> {
                parseChildren(node, scanner)
            }
        }
    }

    private fun AnnotatedString.Builder.parseChildren(node: Node, scanner: MarkScanner) {
        var child = node.firstChild
        while (child != null) {
            parseNode(child, scanner)
            child = child.next
        }
    }
}

/**
 * Streaming scanner for the private-use marks embedded in the text.
 *
 * Toggle marks ([STRIKETHROUGH_MARK], [SUBSCRIPT_MARK], [SUPERSCRIPT_MARK])
 * each flip their own state, so the styles compose with each other and with
 * whatever emphasis the surrounding nodes already applied. Reference runs
 * ([NOTE_REF_MARK]/[ANCHOR_REF_MARK] … [REF_END_MARK]) become tappable
 * [LinkAnnotation.Clickable] spans.
 *
 * The scanner instance lives for a whole [MarkdownParser.parse] call, because
 * commonmark can split one line into several [Text] nodes — the state must
 * survive the node boundaries.
 */
private class MarkScanner(private val builder: AnnotatedString.Builder) {
    private val active = mutableSetOf<Char>()
    private val segment = StringBuilder()

    private var refMark: Char? = null
    private var refInText = false
    private val refId = StringBuilder()
    private val refText = StringBuilder()

    fun append(text: String) {
        text.forEach { char -> consume(char) }
        // Only the toggle state may survive an append() call — text is
        // flushed so it stays inside the builder's current style scope
        flush()
    }

    private fun consume(char: Char) {
        when {
            refMark != null -> consumeRef(char)

            char in MARK_STYLES -> {
                flush()
                if (!active.remove(char)) active.add(char)
            }

            char == NOTE_REF_CHAR || char == ANCHOR_REF_CHAR -> {
                flush()
                refMark = char
                refInText = false
                refId.clear()
                refText.clear()
            }

            else -> segment.append(char)
        }
    }

    private fun consumeRef(char: Char) {
        when {
            char == REF_SEPARATOR_CHAR -> refInText = true
            char == REF_END_CHAR -> emitRef()
            refInText -> refText.append(char)
            else -> refId.append(char)
        }
    }

    private fun emitRef() {
        val mark = refMark
        refMark = null

        val id = try {
            refId.toString().decodeReferenceId()
        } catch (e: Exception) {
            refId.toString()
        }
        // The reference text may carry inline sentinels (e.g. a note number
        // wrapped in <strong>); the run is appended as one plain span, so
        // strip them rather than let private-use characters reach the marker.
        val text = refText.toString().clearInlineMarks()
        if (text.isBlank()) return

        val (tag, style) = if (mark == NOTE_REF_CHAR) {
            // Footnote marker: superscript, slightly smaller
            "$NOTE_LINK_TAG_PREFIX$id" to SpanStyle(
                baselineShift = BaselineShift.Superscript,
                fontSize = 0.75.em
            )
        } else {
            // Plain internal link: styled like an external one
            "$ANCHOR_LINK_TAG_PREFIX$id" to SpanStyle(
                textDecoration = TextDecoration.Underline
            )
        }

        builder.withLink(
            LinkAnnotation.Clickable(
                tag = tag,
                styles = TextLinkStyles(style = style),
                // The listener is injected at render time — parsed text is
                // data and cannot reach the reader's event handlers
                linkInteractionListener = null
            )
        ) {
            builder.append(text)
        }
    }

    private fun flush() {
        if (segment.isEmpty()) return
        active
            .map { mark -> MARK_STYLES.getValue(mark) }
            .reduceOrNull { merged, style -> merged.merge(style) }
            ?.let { style -> builder.withStyle(style) { builder.append(segment.toString()) } }
            ?: builder.append(segment.toString())
        segment.clear()
    }
}
